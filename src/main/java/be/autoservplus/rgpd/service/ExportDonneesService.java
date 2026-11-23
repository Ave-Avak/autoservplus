package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
import be.autoservplus.identite.repository.ModificationCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.rgpd.repository.AjoutAuPanier;
import be.autoservplus.rgpd.repository.CommandeExportRepository;
import be.autoservplus.rgpd.repository.FactureExportRepository;
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
 * Export des données personnelles d'un membre (F22 : droit d'accès, article 15
 * RGPD).
 *
 * <p>Service d'agrégation <b>strictement en lecture</b> : {@code readOnly = true}
 * sur toute la classe, et les repositories du module étendent
 * {@code Repository} sans méthode d'écriture. Exercer un droit d'accès ne modifie
 * rien : la seule trace laissée est l'horodatage en mémoire de
 * {@link RegistreExportsRecents}, qui sert la limite de fréquence et ne touche pas
 * au dossier de la personne.
 *
 * <p><b>Deux gardes</b> avant qu'un octet ne soit produit, dans cet ordre :
 * <ol>
 *   <li>le mot de passe est reconfirmé, car une session ouverte ne suffit pas à
 *       rassembler tout le dossier d'une personne en un seul fichier.</li>
 *   <li>la limite d'un export par 24 heures est vérifiée.</li>
 * </ol>
 * L'ordre n'est pas indifférent : une mauvaise saisie de mot de passe ne doit ni
 * consommer le quota, ni renseigner sur la fréquence des exports d'un compte dont
 * on ne détient pas le mot de passe.
 *
 * <p><b>Étanchéité</b> : l'identité vient du contexte de sécurité, transmise en
 * adresse de courriel par le contrôleur ({@code @AuthenticationPrincipal}), jamais
 * d'un paramètre de requête. Chaque requête du module filtre sur cette adresse : il
 * n'existe aucun chemin par lequel l'export d'un membre pourrait contenir la donnée
 * d'un autre. L'ownership n'est pas vérifié après coup, il est la condition même du
 * chargement.
 *
 * <p><b>Chargement</b> : {@code open-in-view} étant désactivé, chaque requête
 * charge d'avance ce que la conversion en objet de transfert lira. Le coût est
 * borné (une requête par section, deux pour les commandes et leurs lignes)
 * indépendamment du volume : aucun N+1.
 *
 * <p><b>Suppressions logiques</b> : une ligne masquée par {@code deleted_at} est
 * toujours détenue, l'article 15 la vise donc. Le parc du membre est lu par
 * requête native pour passer outre le {@code @SQLRestriction} de {@code Vehicule} :
 * seule entité que l'application supprime effectivement de façon logique, et
 * chaque véhicule sort marqué {@code supprime} avec sa date. Les sections atelier
 * en dépendent : joindre {@code Vehicule} y aurait fait disparaître les
 * rendez-vous et interventions d'un véhicule retiré du parc, alors que la
 * suppression est logique précisément pour préserver cet historique. La plaque y
 * est donc rapprochée depuis ce parc, jamais lue sur la relation.
 *
 * <p><b>Périmètre non couvert en V1</b>, faute d'entité JPA (les tables existent
 * au schéma mais aucun code ne les alimente) : avis, messagerie, notifications.
 * L'export ne comporte donc pas ces sections plutôt que des tableaux vides
 * trompeurs.
 *
 * <p><b>Factures</b> : restituées depuis l'entité {@code Facture} du module
 * facturation, et non déduites des commandes payées. Une commande payée n'est pas
 * une facture : cette dernière porte un numéro légal d'une suite continue, sa
 * propre date d'émission et des montants figés à l'émission. Les deux sections
 * coexistent : une commande annulée n'a pas de facture, et la commande reste une
 * donnée détenue à part entière.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ExportDonneesService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ExportDonneesService.class);

    /**
     * Nom du fichier remis, date du jour incluse. Volontairement non traduit : ce
     * n'est pas un libellé d'interface mais l'identité d'un document que la
     * personne archivera, et qui doit rester reconnaissable d'un export à l'autre.
     */
    private static final String MOTIF_NOM_FICHIER = "mes-donnees-%s.json";

    private final UtilisateurRepository utilisateurs;
    private final VehiculeExportRepository vehicules;
    private final RdvExportRepository rendezVous;
    private final InterventionExportRepository interventions;
    private final CommandeExportRepository commandes;
    private final FactureExportRepository factures;
    private final ConsentementExportRepository consentements;
    private final PanierRepository paniers;
    private final PanierExportRepository panierExport;
    private final EvenementSecuriteCompteRepository evenementsSecurite;
    private final ModificationCompteRepository modificationsCompte;
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
                                FactureExportRepository factures,
                                ConsentementExportRepository consentements,
                                PanierRepository paniers,
                                PanierExportRepository panierExport,
                                EvenementSecuriteCompteRepository evenementsSecurite,
                                ModificationCompteRepository modificationsCompte,
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
        this.factures = factures;
        this.consentements = consentements;
        this.paniers = paniers;
        this.panierExport = panierExport;
        this.evenementsSecurite = evenementsSecurite;
        this.modificationsCompte = modificationsCompte;
        this.catalogue = catalogue;
        this.serialiseur = serialiseur;
        this.registre = registre;
        this.encodeur = encodeur;
        this.horloge = horloge;
    }

    /**
     * Produit le fichier d'export après les deux gardes de F22.
     *
     * <p>Le quota n'est consommé qu'une fois le document réellement produit : une
     * sérialisation qui échouerait ne doit pas priver le membre de son droit
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
     * Temps restant avant un nouvel export. Vide si le membre peut exporter.
     *
     * <p>Expose au contrôleur ce que la garde utilise elle-même : l'écran annonce
     * l'échéance avant la tentative, il ne la découvre pas par un refus.
     */
    public Optional<Duration> attenteRestante(String email) {
        return registre.attenteRestante(email);
    }

    /**
     * Assemble l'export complet du membre : ses données, le rappel légal du
     * traitement, et les notes d'exclusion.
     *
     * <p>Le rappel légal et les exclusions sont résolus dans la <b>langue du
     * membre</b> (colonne {@code langue}), pas dans celle de la requête : le
     * fichier se relit longtemps après la session qui l'a produit.
     *
     * @throws RessourceIntrouvableException si l'adresse ne correspond à aucun
     *         compte : impossible pour un utilisateur authentifié, mais un export
     *         ne se construit pas sur un titulaire supposé
     */
    public ExportDonnees assembler(String email) {
        return assembler(charger(email), email);
    }

    /**
     * Refus si le mot de passe fourni ne correspond pas à l'empreinte du compte.
     *
     * <p>La comparaison passe par l'encodeur du projet ({@code BCrypt}, coût 12),
     * jamais par une égalité de chaînes : l'empreinte ne se compare pas, elle se
     * vérifie. Le {@code null} est écarté avant l'appel ({@code BCrypt} ne
     * l'accepte pas) et vaut échec, pas exception technique.
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
        return utilisateurs.findByEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    /** Date du jour à l'horloge injectée : le nom du fichier reste testable. */
    private String nomDuFichier() {
        return MOTIF_NOM_FICHIER.formatted(LocalDate.now(horloge));
    }

    private ExportDonnees assembler(Utilisateur membre, String email) {
        Locale langue = Locale.forLanguageTag(membre.getLangue().name());
        List<Consentement> preuves = consentements.pourMembre(email);

        // Parc complet du membre, suppressions logiques comprises : c'est la seule
        // liste qui dise ce qui est réellement détenu, et elle sert de référence
        // aux sections atelier, dont les jointures vers Vehicule auraient filtré
        // les dossiers d'un véhicule retiré du parc.
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
                        factures.pourMembre(email).stream()
                                .map(ExportDonnees.FactureExport::de)
                                .toList(),
                        rendezVous.pourMembre(email).stream()
                                .map(rdv -> ExportDonnees.RdvExport.de(rdv, plaque(plaques, rdv.getVehicule())))
                                .toList(),
                        interventionsDuParc(plaques),
                        preuves.stream().map(ExportDonnees.ConsentementExport::de).toList(),
                        ExportDonnees.ConnexionExport.de(membre,
                                evenementsSecurite.evenementsDe(membre.getId())),
                        modificationsCompte.historiqueDe(membre.getId()).stream()
                                .map(ExportDonnees.ModificationCompteExport::de)
                                .toList()),
                catalogue.informationsTraitement(langue),
                catalogue.exclusions(langue));
    }

    /**
     * Interventions portant sur les véhicules du membre, y compris ceux qu'il a
     * retirés de son parc.
     *
     * <p>Sans véhicule, pas d'intervention possible : le cas sort avant la requête
     * plutôt que de soumettre un {@code IN ()} vide à la base.
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
     * Plaque du véhicule, rapprochée par identifiant.
     *
     * <p>Lire {@code vehicule.getPlaque()} directement ferait initialiser le proxy
     * paresseux, et l'initialisation par identifiant applique le
     * {@code @SQLRestriction} : un véhicule supprimé lèverait
     * {@code EntityNotFoundException}. Seul l'identifiant est lu sur le proxy : il
     * vient de la clé étrangère, sans accès à la base.
     */
    private static String plaque(Map<Long, String> plaques, Vehicule vehicule) {
        return vehicule == null ? null : plaques.get(vehicule.getId());
    }

    /**
     * Panier en cours du membre, lignes comprises.
     *
     * <p>Le contenu vient du repository du module {@code vente}, dont la requête
     * charge déjà panier, lignes et pièces d'un coup. La date d'ajout de chaque
     * ligne, elle, passe par une projection du module {@code rgpd} : l'entité
     * mappe {@code created_at} mais ne l'expose pas, et lui ajouter un accesseur
     * modifierait {@code vente}.
     *
     * <p>Une lecture ne crée jamais de panier : RM-19 réserve le trouve-ou-crée aux
     * chemins d'écriture. Un membre sans panier obtient une section vide, pas une
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
     * commandes sont ramenées en une seule requête puis regroupées en mémoire :
     * deux requêtes au total, quel que soit le nombre de commandes.
     *
     * <p>Le cas « aucune commande » sort avant la seconde requête : un
     * {@code IN ()} vide n'a pas de sens et n'a pas à être soumis à la base.
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
