package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.rgpd.repository.AjoutAuPanier;
import be.autoservplus.rgpd.repository.CommandeExportRepository;
import be.autoservplus.rgpd.repository.ConsentementExportRepository;
import be.autoservplus.rgpd.repository.InterventionExportRepository;
import be.autoservplus.rgpd.repository.PanierExportRepository;
import be.autoservplus.rgpd.repository.RdvExportRepository;
import be.autoservplus.rgpd.repository.VehiculeExportRepository;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.rgpd.service.dto.FichierExport;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.PanierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Export des donnees personnelles d un membre (F22 — droit d acces, article 15
 * RGPD).
 *
 * <p>Service d agregation <b>strictement en lecture</b> : {@code readOnly = true}
 * sur toute la classe, et les repositories du module etendent
 * {@code Repository} sans methode d ecriture. Exercer un droit d acces ne modifie
 * rien — la seule trace laissee est l horodatage en memoire de
 * {@link RegistreExportsRecents}, qui sert la limite de frequence et ne touche pas
 * au dossier de la personne.
 *
 * <p><b>Deux gardes</b> avant qu un octet ne soit produit, dans cet ordre :
 * <ol>
 *   <li>le mot de passe est reconfirme — une session ouverte ne suffit pas a
 *       rassembler tout le dossier d une personne en un seul fichier ;</li>
 *   <li>la limite d un export par 24 heures est verifiee.</li>
 * </ol>
 * L ordre n est pas indifferent : une mauvaise saisie de mot de passe ne doit ni
 * consommer le quota, ni renseigner sur la frequence des exports d un compte dont
 * on ne detient pas le mot de passe.
 *
 * <p><b>Etancheite</b> : l identite vient du contexte de securite, transmise en
 * adresse de courriel par le controleur ({@code @AuthenticationPrincipal}), jamais
 * d un parametre de requete. Chaque requete du module filtre sur cette adresse : il
 * n existe aucun chemin par lequel l export d un membre pourrait contenir la donnee
 * d un autre — l ownership n est pas verifie apres coup, il est la condition meme du
 * chargement.
 *
 * <p><b>Chargement</b> : {@code open-in-view} etant desactive, chaque requete
 * charge d avance ce que la conversion en objet de transfert lira. Le cout est
 * borne — une requete par section, deux pour les commandes et leurs lignes —
 * independamment du volume : aucun N+1.
 *
 * <p><b>Suppressions logiques</b> : une ligne masquee par {@code deleted_at} est
 * toujours detenue, l article 15 la vise donc. Le parc du membre est lu par
 * requete native pour passer outre le {@code @SQLRestriction} de {@code Vehicule}
 * — seule entite que l application supprime effectivement de facon logique — et
 * chaque vehicule sort marque {@code supprime} avec sa date. Les sections atelier
 * en dependent : joindre {@code Vehicule} y aurait fait disparaitre les
 * rendez-vous et interventions d un vehicule retire du parc, alors que la
 * suppression est logique precisement pour preserver cet historique. La plaque y
 * est donc rapprochee depuis ce parc, jamais lue sur la relation.
 *
 * <p><b>Perimetre non couvert en V1</b>, faute d entite JPA (les tables existent
 * au schema mais aucun code ne les alimente) : avis, messagerie, notifications.
 * L export ne comporte donc pas ces sections plutot que des tableaux vides
 * trompeurs. Les factures suivront le module facturation ; les commandes payees,
 * montants compris, sont deja restituees ici.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ExportDonneesService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ExportDonneesService.class);

    /**
     * Nom du fichier remis, date du jour incluse. Volontairement non traduit : ce
     * n est pas un libelle d interface mais l identite d un document que la
     * personne archivera, et qui doit rester reconnaissable d un export a l autre.
     */
    private static final String MOTIF_NOM_FICHIER = "mes-donnees-%s.json";

    private final UtilisateurRepository utilisateurs;
    private final VehiculeExportRepository vehicules;
    private final RdvExportRepository rendezVous;
    private final InterventionExportRepository interventions;
    private final CommandeExportRepository commandes;
    private final ConsentementExportRepository consentements;
    private final PanierRepository paniers;
    private final PanierExportRepository panierExport;
    private final CatalogueTraitements catalogue;
    private final SerialiseurExportJson serialiseur;
    private final RegistreExportsRecents registre;
    private final PasswordEncoder encodeur;
    private final Clock horloge;

    public ExportDonneesService(UtilisateurRepository utilisateurs,
                                VehiculeExportRepository vehicules,
                                RdvExportRepository rendezVous,
                                InterventionExportRepository interventions,
                                CommandeExportRepository commandes,
                                ConsentementExportRepository consentements,
                                PanierRepository paniers,
                                PanierExportRepository panierExport,
                                CatalogueTraitements catalogue,
                                SerialiseurExportJson serialiseur,
                                RegistreExportsRecents registre,
                                PasswordEncoder encodeur,
                                Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.vehicules = vehicules;
        this.rendezVous = rendezVous;
        this.interventions = interventions;
        this.commandes = commandes;
        this.consentements = consentements;
        this.paniers = paniers;
        this.panierExport = panierExport;
        this.catalogue = catalogue;
        this.serialiseur = serialiseur;
        this.registre = registre;
        this.encodeur = encodeur;
        this.horloge = horloge;
    }

    /**
     * Produit le fichier d export apres les deux gardes de F22.
     *
     * <p>Le quota n est consomme qu une fois le document reellement produit : une
     * serialisation qui echouerait ne doit pas priver le membre de son droit
     * pendant vingt-quatre heures.
     *
     * @throws ReauthentificationEchoueeException si le mot de passe ne correspond pas
     * @throws ExportTropRecentException          si un export date de moins de 24 heures
     */
    public FichierExport exporter(String email, String motDePasse) {
        Utilisateur membre = charger(email);
        exigerMotDePasse(membre, motDePasse);
        exigerDelaiEcoule(email);

        byte[] contenu = serialiseur.enJson(assembler(membre, email));
        registre.enregistrer(email);
        JOURNAL.info("Export RGPD produit pour le compte {}", membre.getReference());
        return new FichierExport(nomDuFichier(), contenu);
    }

    /**
     * Temps restant avant un nouvel export ; vide si le membre peut exporter.
     *
     * <p>Expose au controleur ce que la garde utilise elle-meme : l ecran annonce
     * l echeance avant la tentative, il ne la decouvre pas par un refus.
     */
    public Optional<Duration> attenteRestante(String email) {
        return registre.attenteRestante(email);
    }

    /**
     * Assemble l export complet du membre : ses donnees, le rappel legal du
     * traitement, et les notes d exclusion.
     *
     * <p>Le rappel legal et les exclusions sont resolus dans la <b>langue du
     * membre</b> (colonne {@code langue}), pas dans celle de la requete : le
     * fichier se relit longtemps apres la session qui l a produit.
     *
     * @throws RessourceIntrouvableException si l adresse ne correspond a aucun
     *         compte — impossible pour un utilisateur authentifie, mais un export
     *         ne se construit pas sur un titulaire suppose
     */
    public ExportDonnees assembler(String email) {
        return assembler(charger(email), email);
    }

    /**
     * Refus si le mot de passe fourni ne correspond pas a l empreinte du compte.
     *
     * <p>La comparaison passe par l encodeur du projet ({@code BCrypt}, cout 12),
     * jamais par une egalite de chaines : l empreinte ne se compare pas, elle se
     * verifie. Le {@code null} est ecarte avant l appel — {@code BCrypt} ne
     * l accepte pas — et vaut echec, pas exception technique.
     */
    private void exigerMotDePasse(Utilisateur membre, String motDePasse) {
        if (motDePasse == null || motDePasse.isEmpty()
                || !encodeur.matches(motDePasse, membre.getMotDePasseHache())) {
            JOURNAL.warn("Export RGPD refuse : re-authentification echouee pour {}",
                    membre.getReference());
            throw new ReauthentificationEchoueeException();
        }
    }

    private void exigerDelaiEcoule(String email) {
        registre.attenteRestante(email).ifPresent(attente -> {
            throw new ExportTropRecentException(attente);
        });
    }

    private Utilisateur charger(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    /** Date du jour a l horloge injectee : le nom du fichier reste testable. */
    private String nomDuFichier() {
        return MOTIF_NOM_FICHIER.formatted(LocalDate.now(horloge));
    }

    private ExportDonnees assembler(Utilisateur membre, String email) {
        Locale langue = Locale.forLanguageTag(membre.getLangue().name());
        List<Consentement> preuves = consentements.pourMembre(email);

        // Parc complet du membre, suppressions logiques comprises : c'est la seule
        // liste qui dise ce qui est reellement detenu, et elle sert de reference
        // aux sections atelier — dont les jointures vers Vehicule auraient filtre
        // les dossiers d'un vehicule retire du parc.
        List<Vehicule> parc = vehicules.pourMembre(membre.getId());
        Map<Long, String> plaques = parc.stream()
                .collect(Collectors.toMap(Vehicule::getId, Vehicule::getPlaque));

        return new ExportDonnees(
                Instant.now(horloge),
                new ExportDonnees.DonneesPersonnelles(
                        ExportDonnees.ProfilExport.de(membre, preuves),
                        parc.stream().map(ExportDonnees.VehiculeExport::de).toList(),
                        panierEnCours(email),
                        commandesAvecLignes(email),
                        rendezVous.pourMembre(email).stream()
                                .map(rdv -> ExportDonnees.RdvExport.de(rdv, plaque(plaques, rdv.getVehicule())))
                                .toList(),
                        interventionsDuParc(plaques),
                        preuves.stream().map(ExportDonnees.ConsentementExport::de).toList(),
                        ExportDonnees.ConnexionExport.de(membre)),
                catalogue.informationsTraitement(langue),
                catalogue.exclusions(langue));
    }

    /**
     * Interventions portant sur les vehicules du membre, y compris ceux qu il a
     * retires de son parc.
     *
     * <p>Sans vehicule, pas d intervention possible : le cas sort avant la requete
     * plutot que de soumettre un {@code IN ()} vide a la base.
     */
    private List<ExportDonnees.InterventionExport> interventionsDuParc(Map<Long, String> plaques) {
        if (plaques.isEmpty()) {
            return List.of();
        }
        return interventions.pourVehicules(plaques.keySet()).stream()
                .map(intervention -> ExportDonnees.InterventionExport.de(intervention,
                        plaque(plaques, intervention.getVehicule())))
                .toList();
    }

    /**
     * Plaque du vehicule, rapprochee par identifiant.
     *
     * <p>Lire {@code vehicule.getPlaque()} directement ferait initialiser le proxy
     * paresseux, et l initialisation par identifiant applique le
     * {@code @SQLRestriction} : un vehicule supprime leverait
     * {@code EntityNotFoundException}. Seul l identifiant est lu sur le proxy — il
     * vient de la cle etrangere, sans acces a la base.
     */
    private static String plaque(Map<Long, String> plaques, Vehicule vehicule) {
        return vehicule == null ? null : plaques.get(vehicule.getId());
    }

    /**
     * Panier en cours du membre, lignes comprises.
     *
     * <p>Le contenu vient du repository du module {@code vente}, dont la requete
     * charge deja panier, lignes et pieces d un coup. La date d ajout de chaque
     * ligne, elle, passe par une projection du module {@code rgpd} : l entite
     * mappe {@code created_at} mais ne l expose pas, et lui ajouter un accesseur
     * modifierait {@code vente}.
     *
     * <p>Une lecture ne cree jamais de panier — RM-19 reserve le trouve-ou-cree aux
     * chemins d ecriture. Un membre sans panier obtient une section vide, pas une
     * ligne en base.
     */
    private ExportDonnees.PanierExport panierEnCours(String email) {
        return paniers.findByMembreEmail(email)
                .map(panier -> ExportDonnees.PanierExport.de(panier, datesAjout(panier)))
                .orElseGet(ExportDonnees.PanierExport::vide);
    }

    private Map<Long, Instant> datesAjout(Panier panier) {
        return panierExport.datesAjout(panier.getId()).stream()
                .collect(Collectors.toMap(AjoutAuPanier::ligneId, AjoutAuPanier::dateAjout));
    }

    /**
     * Commandes du membre, chacune avec ses lignes. Les lignes de toutes les
     * commandes sont ramenees en une seule requete puis regroupees en memoire :
     * deux requetes au total, quel que soit le nombre de commandes.
     *
     * <p>Le cas « aucune commande » sort avant la seconde requete : un
     * {@code IN ()} vide n a pas de sens et n a pas a etre soumis a la base.
     */
    private List<ExportDonnees.CommandeExport> commandesAvecLignes(String email) {
        List<Commande> commandesDuMembre = commandes.pourMembre(email);
        if (commandesDuMembre.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LignePanier>> lignesParCommande = commandes.lignesDe(commandesDuMembre)
                .stream()
                .collect(Collectors.groupingBy(ligne -> ligne.getCommande().getId()));

        return commandesDuMembre.stream()
                .map(commande -> ExportDonnees.CommandeExport.de(commande,
                        lignesParCommande.getOrDefault(commande.getId(), List.of())))
                .toList();
    }
}
