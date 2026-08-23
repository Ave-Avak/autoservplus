package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
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
 * Suppression de compte par anonymisation (F23, RM-05 — article 17 RGPD).
 *
 * <p><b>Le droit a l effacement n est pas absolu.</b> L article 17.3.b ecarte
 * l obligation d effacer quand le traitement sert une obligation legale : le Code de
 * la TVA (art. 60) impose sept ans de conservation des factures, et une facture doit
 * porter l identite du client. Les deux exigences ne s opposent pas, elles ne portent
 * pas sur le meme objet. Le document comptable reste intact — ni regenere, ni
 * modifie ; la ligne applicative qui designe la personne est videe.</p>
 *
 * <p><b>Ce service n ecrit donc jamais une donnee comptable.</b> Les tables facture,
 * avoir et paiement ne stockent aucun nom : elles pointent {@code commande} puis
 * {@code utilisateur}. Le « Client supprime » du CdC n est pas un champ a reecrire
 * document par document, c est l effet automatique de l anonymisation d une seule
 * ligne, lue par relation.</p>
 *
 * <p><b>Deux gardes avant tout effet de bord</b>, et dans cet ordre : le mot de passe
 * prouve l identite, le mot recopie prouve l intention. Un echec de l une ou l autre
 * ne laisse aucune trace d ecriture — les verifications precedent la premiere
 * mutation, elles ne la rattrapent pas.</p>
 *
 * <p><b>Ordre transactionnel</b> : capture de l adresse, vehicules, compte, flush,
 * puis balayage des traces d audit. Le flush n est pas cosmetique — sans lui, l audit
 * JPA reecrirait {@code updated_by} avec l adresse du membre <i>apres</i> le passage
 * du balayage, et l adresse reapparaitrait sur la ligne qu on vient de vider.</p>
 *
 * <p>Vit dans le module rgpd, aux cotes de l export F22 : les deux servent des droits
 * de la meme personne sur les memes donnees, partagent la re-authentification, et
 * dependent deja des modules identite et reservation sans qu aucun ne depende
 * d eux.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class SuppressionCompteService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SuppressionCompteService.class);

    /**
     * Mot a recopier pour confirmer. <b>Constante, identique dans les trois langues</b>
     * et affichee telle quelle par l ecran. Le traduire ferait dependre une garde de
     * securite d une chaine d interface : le service devrait alors connaitre la locale
     * de la session, et un test ne pourrait plus l exercer sans contexte web. C est
     * l usage courant des confirmations destructrices, ou l on recopie un jeton exact
     * plutot qu un mot traduit.
     */
    public static final String MOT_DE_CONFIRMATION = "SUPPRIMER";

    /**
     * Domaine reserve par la RFC 2606 : aucun serveur de messagerie ne peut l heberger,
     * aucun envoi accidentel ne partira vers un compte anonymise.
     */
    private static final String DOMAINE_ANONYME = "@supprime.invalid";

    private static final SecureRandom ALEA = new SecureRandom();

    private final UtilisateurRepository utilisateurs;
    private final VehiculeAnonymisationRepository vehicules;
    private final TracesAuditRepository tracesAudit;
    private final PasswordEncoder encodeur;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public SuppressionCompteService(UtilisateurRepository utilisateurs,
                                    VehiculeAnonymisationRepository vehicules,
                                    TracesAuditRepository tracesAudit,
                                    PasswordEncoder encodeur,
                                    ApplicationEventPublisher evenements,
                                    Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.vehicules = vehicules;
        this.tracesAudit = tracesAudit;
        this.encodeur = encodeur;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    /**
     * Anonymise le compte du membre connecte et son parc, puis publie l evenement de
     * notification.
     *
     * <p>L identite vient du contexte de securite, transmise par le controleur : il
     * n existe aucun parametre permettant de designer un autre titulaire, donc aucune
     * URL par laquelle un membre viserait le compte d autrui.</p>
     *
     * @param motDePasse   mot de passe du compte, re-authentification
     * @param confirmation mot recopie par le membre, doit valoir {@value #MOT_DE_CONFIRMATION}
     * @throws RessourceIntrouvableException             adresse inconnue
     * @throws ReauthentificationEchoueeException        mot de passe incorrect
     * @throws ConfirmationSuppressionInvalideException  mot de confirmation absent ou faux
     * @throws IllegalStateException                     compte deja anonymise, ou administrateur
     */
    @Transactional
    public void supprimer(String email, String motDePasse, String confirmation) {
        Utilisateur membre = utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));

        exigerMotDePasse(membre, motDePasse);
        exigerConfirmation(membre, confirmation);

        // Capture AVANT tout ecrasement : apres l anonymisation, l adresse reelle
        // n existe plus nulle part et le courriel de confirmation n aurait plus de
        // destinataire.
        String adresseReelle = membre.getEmail();
        CompteSupprimeEvent evenement =
                new CompteSupprimeEvent(adresseReelle, membre.getPrenom(), membre.getReference());

        int supprimes = anonymiserLeParc(membre);
        membre.anonymiser(jetonEmail(membre), hachageInerte(), horloge.instant());
        // Flush avant le balayage : voir TracesAuditRepository, l ordre est impose.
        utilisateurs.saveAndFlush(membre);
        int traces = nettoyerLesTracesDAudit(adresseReelle, membre.getEmail());

        // Journal sans donnee personnelle : la reference identifie la ligne, jamais
        // la personne. Une trace de suppression qui contiendrait le nom ou l adresse
        // recreerait ce que la suppression vient d effacer.
        JOURNAL.info("Compte {} anonymise : {} vehicule(s) supprime(s), {} trace(s) d audit nettoyee(s).",
                membre.getReference(), supprimes, traces);

        evenements.publishEvent(evenement);
    }

    // --- gardes -------------------------------------------------------------------------

    /**
     * Refus si le mot de passe fourni ne correspond pas a l empreinte du compte.
     * Meme mecanique que l export F22 : la comparaison passe par l encodeur du projet
     * (BCrypt, cout 12), le {@code null} vaut echec et non exception technique.
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
     * Refus si le mot de confirmation n est pas recopie exactement. Comparaison
     * sensible a la casse et sur la valeur nettoyee de ses espaces : un
     * « supprimer » minuscule signale une saisie machinale plutot qu une intention.
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
     * Traite le parc du membre, vehicules supprimes logiquement compris — ils portent
     * encore leur plaque.
     *
     * <p><b>Ecart assume avec la lettre du CdC</b>, qui dit « suppression des
     * vehicules ». {@code vehicule.membre_id} et les trois FK entrantes sont en
     * {@code ON DELETE RESTRICT} : un vehicule qu un rendez-vous, une intervention ou
     * une reservation de parking reference ne peut pas disparaitre sans emporter
     * l historique que la loi et le garage conservent. Le patron est celui de RM-29
     * pour les prestations et pieces : suppression physique si rien ne reference,
     * anonymisation sinon. L esprit du CdC — plus aucune donnee identifiante — est
     * tenu dans les deux cas.</p>
     *
     * @return le nombre de vehicules reellement supprimes physiquement
     */
    private int anonymiserLeParc(Utilisateur membre) {
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

    private int nettoyerLesTracesDAudit(String adresseReelle, String jeton) {
        Integer modifiees = tracesAudit.anonymiser(adresseReelle, jeton);
        return modifiees == null ? 0 : modifiees;
    }

    /**
     * Adresse de substitution, unique et non routable. L identifiant technique la rend
     * unique — {@code uq_utilisateur_email} porte sur toute la table, sans exception
     * pour les lignes supprimees logiquement. Consequence <b>voulue</b> : l adresse
     * reelle redevient libre, la personne peut se reinscrire.
     */
    private static String jetonEmail(Utilisateur membre) {
        return "anonyme-" + membre.getId() + DOMAINE_ANONYME;
    }

    /**
     * Plaque de substitution, unique parmi les vehicules non supprimes logiquement
     * ({@code uq_vehicule_plaque_active}). Quinze caracteres au plus, la colonne ne
     * va pas au-dela.
     */
    private static String plaqueAnonyme(Vehicule vehicule) {
        return "ANON-" + vehicule.getId();
    }

    /**
     * Empreinte BCrypt d un secret aleatoire immediatement perdu.
     *
     * <p>Un vrai hachage et non une constante : la colonne fait soixante caracteres et
     * l encodeur compare des empreintes au format attendu. Une chaine hors format
     * ferait echouer la verification sur une exception technique au lieu d un refus
     * propre. Le secret n est stocke nulle part et n est jamais retourne — plus
     * personne, y compris le garage, ne peut se connecter a ce compte.</p>
     */
    private String hachageInerte() {
        byte[] secret = new byte[32];
        ALEA.nextBytes(secret);
        return encodeur.encode(Base64.getEncoder().encodeToString(secret));
    }
}
