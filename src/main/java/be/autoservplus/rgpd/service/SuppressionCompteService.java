package be.autoservplus.rgpd.service;

import be.autoservplus.avis.domain.Avis;
import be.autoservplus.avis.repository.AvisRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.devis.repository.DevisRepository;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.CodeSecoursRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.HistoriqueModificationsCompte;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.rgpd.repository.TracesAuditRepository;
import be.autoservplus.rgpd.repository.VehiculeAnonymisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Suppression de compte par anonymisation (F23, RM-05 : article 17 RGPD).
 *
 * <p><b>Le droit à l'effacement n'est pas absolu.</b> L'article 17.3.b écarte
 * l'obligation d'effacer quand le traitement sert une obligation légale : le Code de
 * la TVA (art. 60) impose dix ans de conservation des factures, et une facture doit
 * porter l'identité du client. Les deux exigences ne s'opposent pas, elles ne portent
 * pas sur le même objet. Le document comptable reste intact, ni régénéré, ni
 * modifié. La ligne applicative qui désigne la personne est vidée.</p>
 *
 * <p><b>Ce service n'écrit donc jamais une donnée comptable.</b> Les tables facture,
 * avoir et paiement ne stockent aucun nom : elles pointent {@code commande} puis
 * {@code utilisateur}. Le « Client supprimé » du CdC n'est pas un champ à réécrire
 * document par document, c'est l'effet automatique de l'anonymisation d'une seule
 * ligne, lue par relation.</p>
 *
 * <p><b>Deux gardes avant tout effet de bord</b>, et dans cet ordre : le mot de passe
 * prouve l'identité, le mot recopié prouve l'intention. Un échec de l'une ou l'autre
 * ne laisse aucune trace d'écriture. Les vérifications précédent la première
 * mutation, elles ne la rattrapent pas.</p>
 *
 * <p><b>Ordre transactionnel</b> : capture de l'adresse, devis, véhicules, compte, flush,
 * puis balayage des traces d'audit. Le flush n'est pas cosmétique. Sans lui, l'audit
 * JPA réécrirait {@code updated_by} avec l'adresse du membre <i>après</i> le passage
 * du balayage, et l'adresse réapparaîtrait sur la ligne qu'on vient de vider.</p>
 *
 * <p>Vit dans le module rgpd, aux côtés de l'export F22 : les deux servent des droits
 * de la même personne sur les mêmes données, partagent la re-authentification, et
 * dépendent déjà des modules identité et réservation sans qu'aucun ne dépende
 * d'eux.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class SuppressionCompteService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SuppressionCompteService.class);

    /**
     * Mot à recopier pour confirmer. <b>Constante, identique dans les trois langues</b>
     * et affichée telle quelle par l'écran. Le traduire ferait dépendre une garde de
     * sécurité d'une chaîne d'interface : le service devrait alors connaître la locale
     * de la session, et un test ne pourrait plus l'exercer sans contexte web. C'est
     * l'usage courant des confirmations destructrices, où l'on recopie un jeton exact
     * plutôt qu'un mot traduit.
     */
    public static final String MOT_DE_CONFIRMATION = "SUPPRIMER";

    /**
     * Domaine réservé par la RFC 2606 : aucun serveur de messagerie ne peut l'héberger,
     * aucun envoi accidentel ne partira vers un compte anonymisé.
     */
    private static final String DOMAINE_ANONYME = "@supprime.invalid";

    private static final SecureRandom ALEA = new SecureRandom();

    private final UtilisateurRepository utilisateurs;
    private final VehiculeAnonymisationRepository vehicules;
    private final AvisRepository avis;
    private final DevisRepository devis;
    private final TracesAuditRepository tracesAudit;
    private final CodeSecoursRepository codesSecours;
    private final HistoriqueModificationsCompte historiqueCompte;
    private final PasswordEncoder encodeur;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public SuppressionCompteService(UtilisateurRepository utilisateurs,
                                    VehiculeAnonymisationRepository vehicules,
                                    AvisRepository avis,
                                    DevisRepository devis,
                                    TracesAuditRepository tracesAudit,
                                    CodeSecoursRepository codesSecours,
                                    HistoriqueModificationsCompte historiqueCompte,
                                    PasswordEncoder encodeur,
                                    ApplicationEventPublisher evenements,
                                    Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.vehicules = vehicules;
        this.avis = avis;
        this.devis = devis;
        this.tracesAudit = tracesAudit;
        this.codesSecours = codesSecours;
        this.historiqueCompte = historiqueCompte;
        this.encodeur = encodeur;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    /**
     * Anonymise le compte du membre connecté et son parc, puis publie l'événement de
     * notification.
     *
     * <p>L'identité vient du contexte de sécurité, transmise par le contrôleur : il
     * n'existe aucun paramètre permettant de désigner un autre titulaire, donc aucune
     * URL par laquelle un membre viserait le compte d'autrui.</p>
     *
     * @param motDePasse   mot de passe du compte, re-authentification
     * @param confirmation mot recopié par le membre, doit valoir {@value #MOT_DE_CONFIRMATION}
     * @throws RessourceIntrouvableException             adresse inconnue
     * @throws ReauthentificationEchoueeException        mot de passe incorrect
     * @throws ConfirmationSuppressionInvalideException  mot de confirmation absent ou faux
     * @throws IllegalStateException                     compte déjà anonymisé, ou administrateur
     */
    @Transactional
    public void supprimer(String email, String motDePasse, String confirmation) {
        Utilisateur membre = utilisateurs.findByEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));

        exigerMotDePasse(membre, motDePasse);
        exigerConfirmation(membre, confirmation);

        // Capture AVANT tout écrasement : après l'anonymisation, l'adresse réelle
        // n'existe plus nulle part et le courriel de confirmation n'aurait plus de
        // destinataire.
        String adresseReelle = membre.getEmail();
        CompteSupprimeEvent evenement =
                new CompteSupprimeEvent(adresseReelle, membre.getPrenom(), membre.getReference(),
                        membre.getLangue());

        // Les devis partent entiers : aucune obligation ne les fait conserver, et ils
        // recopient le nom, l'adresse et la plaque que l'anonymisation efface ailleurs.
        int devisSupprimes = devis.supprimerDuMembre(membre.getId());
        int supprimes = anonymiserLeParc(membre);
        int avisNeutralises = anonymiserLesAvis(membre);
        // Un ancien nom ou une ancienne adresse resterait lisible dans l'historique du
        // compte : les valeurs partent, le fait de la modification reste.
        int modifications = historiqueCompte.effacerValeurs(membre);
        membre.anonymiser(jetonEmail(membre), hachageInerte(), horloge.instant());
        // Le secret TOTP part avec anonymiser(). Les codes de secours encore valables
        // sont invalidés, et non supprimés : leur date reste la trace de leur fin.
        codesSecours.invaliderUtilisables(membre.getId(), horloge.instant());
        // Flush avant le balayage : voir TracesAuditRepository, l'ordre est imposé.
        utilisateurs.saveAndFlush(membre);
        int traces = nettoyerLesTracesDAudit(adresseReelle, membre.getEmail());

        // Journal sans donnée personnelle : la référence identifie la ligne, jamais
        // la personne. Une trace de suppression qui contiendrait le nom ou l'adresse
        // recréerait ce que la suppression vient d'effacer.
        JOURNAL.info("Compte {} anonymise : {} vehicule(s) supprime(s), {} devis supprime(s), "
                        + "{} avis neutralise(s), {} modification(s) de compte videe(s), "
                        + "{} trace(s) d audit nettoyee(s).",
                membre.getReference(), supprimes, devisSupprimes, avisNeutralises, modifications, traces);

        evenements.publishEvent(evenement);
    }

    // --- gardes -------------------------------------------------------------------------

    /**
     * Refus si le mot de passe fourni ne correspond pas à l'empreinte du compte.
     * Même mécanique que l'export F22 : la comparaison passe par l'encodeur du projet
     * (BCrypt, coût 12), le {@code null} vaut échec et non exception technique.
     */
    private void exigerMotDePasse(Utilisateur membre, String motDePasse) {
        if (motDePasse == null || motDePasse.isEmpty()
                || !encodeur.matches(motDePasse, membre.getMotDePasseHache())) {
            JOURNAL.warn("Suppression de compte refusee : re-authentification echouee pour {}",
                    membre.getReference());
            throw new ReauthentificationEchoueeException();
        }
    }

    /**
     * Refus si le mot de confirmation n'est pas recopié exactement. Comparaison
     * sensible à la casse et sur la valeur nettoyée de ses espaces : un
     * « supprimer » minuscule signale une saisie machinale plutôt qu'une intention.
     */
    private void exigerConfirmation(Utilisateur membre, String confirmation) {
        if (confirmation == null || !MOT_DE_CONFIRMATION.equals(confirmation.strip())) {
            JOURNAL.warn("Suppression de compte refusee : confirmation invalide pour {}",
                    membre.getReference());
            throw new ConfirmationSuppressionInvalideException();
        }
    }

    // --- anonymisation ------------------------------------------------------------------

    /**
     * Traite le parc du membre, véhicules supprimés logiquement compris : ils portent
     * encore leur plaque.
     *
     * <p><b>Écart assumé avec la lettre du CdC</b>, qui dit « suppression des
     * véhicules ». {@code vehicule.membre_id} et les trois FK entrantes sont en
     * {@code ON DELETE RESTRICT} : un véhicule qu'un rendez-vous, une intervention ou
     * une réservation de parking référence ne peut pas disparaître sans emporter
     * l'historique que la loi et le garage conservent. Le patron est celui de RM-29
     * pour les prestations et pièces : suppression physique si rien ne référence,
     * anonymisation sinon. L'esprit du CdC (plus aucune donnée identifiante) est
     * tenu dans les deux cas.</p>
     *
     * <p>Les interventions conservées perdent leur note interne (A16), seul champ libre
     * de l'atelier qui porte sur la personne.</p>
     *
     * @return le nombre de véhicules réellement supprimés physiquement
     */
    private int anonymiserLeParc(Utilisateur membre) {
        // Avant le parc : un véhicule sans historique disparaît physiquement, et la
        // requête rattache les notes par le véhicule.
        vehicules.effacerNotesInternes(membre.getId());
        List<Vehicule> parc = vehicules.tousLesVehicules(membre.getId());
        int supprimes = 0;
        for (Vehicule vehicule : parc) {
            if (vehicules.nombreReferencesHistoriques(vehicule.getId()) == 0) {
                vehicules.supprimerPhysiquement(vehicule.getId());
                supprimes++;
            } else {
                vehicule.anonymiser(plaqueAnonyme(vehicule));
                vehicules.saveAndFlush(vehicule);
            }
        }
        return supprimes;
    }

    /**
     * Neutralise les commentaires d'avis de l'auteur (BL-4, extension F23).
     *
     * <p><b>Le texte disparaît, la note reste.</b> Le commentaire est du champ libre :
     * le membre a pu y écrire son nom, sa plaque, le nom de son garagiste. La note est
     * un chiffre de 1 à 5, qui ne désigne personne : rattachée à une ligne dont
     * l'auteur porte désormais le jeton anonyme, elle n'est pas plus identifiante
     * après qu'avant. L'effacer fausserait des moyennes publiques déjà affichées, sans
     * rien ajouter au droit à l'effacement.</p>
     *
     * <p>L'avis reste <b>publié</b> : le dépublier ferait disparaître une mauvaise note
     * à la faveur d'une suppression de compte, ce qui serait une décision commerciale
     * déguisée en mesure de protection des données.</p>
     *
     * @return le nombre de commentaires réellement effacés
     */
    private int anonymiserLesAvis(Utilisateur membre) {
        List<Avis> aNeutraliser = avis.avecCommentaireDuMembre(membre.getId());
        aNeutraliser.forEach(Avis::anonymiserCommentaire);
        avis.saveAllAndFlush(aNeutraliser);
        return aNeutraliser.size();
    }

    private int nettoyerLesTracesDAudit(String adresseReelle, String jeton) {
        Integer modifiees = tracesAudit.anonymiser(adresseReelle, jeton);
        return modifiees == null ? 0 : modifiees;
    }

    /**
     * Adresse de substitution, unique et non routable. L'identifiant technique la rend
     * unique : {@code uq_utilisateur_email} porte sur toute la table, sans exception
     * pour les lignes supprimées logiquement. Conséquence <b>voulue</b> : l'adresse
     * réelle redevient libre, la personne peut se réinscrire.
     */
    private static String jetonEmail(Utilisateur membre) {
        return "anonyme-" + membre.getId() + DOMAINE_ANONYME;
    }

    /**
     * Plaque de substitution, unique parmi les véhicules non supprimés logiquement
     * ({@code uq_vehicule_plaque_active}). Quinze caractères au plus, la colonne ne
     * va pas au-delà.
     */
    private static String plaqueAnonyme(Vehicule vehicule) {
        return "ANON-" + vehicule.getId();
    }

    /**
     * Empreinte BCrypt d'un secret aléatoire immédiatement perdu.
     *
     * <p>Un vrai hachage et non une constante : la colonne fait soixante caractères et
     * l'encodeur compare des empreintes au format attendu. Une chaîne hors format
     * ferait échouer la vérification sur une exception technique au lieu d'un refus
     * propre. Le secret n'est stocké nulle part et n'est jamais retourné : plus
     * personne, y compris le garage, ne peut se connecter à ce compte.</p>
     */
    private String hachageInerte() {
        byte[] secret = new byte[32];
        ALEA.nextBytes(secret);
        return encodeur.encode(Base64.getEncoder().encodeToString(secret));
    }
}
