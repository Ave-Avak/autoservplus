package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.LiensCourriel;
import be.autoservplus.communication.service.OrigineMotDePasse;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reinitialisation du mot de passe.
 *
 * <p>Le service ne revele jamais si une adresse correspond a un compte : la reponse est
 * toujours identique. L utilisateur obtient l information par le seul canal legitime,
 * sa boite de reception. Le contenu du courriel s adapte a la situation reelle du
 * compte, ce qui evite qu un membre n ayant jamais active son inscription ne reste
 * bloque sans comprendre pourquoi.</p>
 *
 * <p>Le jeton de réinitialisation a ses propres colonnes, séparées de celles de la
 * vérification d'adresse, et seule son empreinte est stockée ({@link JetonLien}).</p>
 */
@Service
@Transactional(readOnly = true)
public class MotDePasseService {

    /**
     * Une heure, et non les quinze minutes d'abord envisagées : le jeton est haché, à
     * usage unique, et son emploi ferme toutes les sessions du compte. Une fenêtre plus
     * courte rendrait inutilisable un courriel simplement retardé.
     */
    public static final Duration VALIDITE_JETON = Duration.ofHours(1);

    private static final Logger JOURNAL = LoggerFactory.getLogger(MotDePasseService.class);

    private final UtilisateurRepository repository;
    private final PolitiqueMotDePasse politique;
    private final PasswordEncoder encodeur;
    private final ServiceCourriel courriel;
    private final Clock horloge;
    private final LiensCourriel liens;
    private final RevocationSessions sessions;
    private final JournalSecuriteCompte journal;

    public MotDePasseService(UtilisateurRepository repository, PasswordEncoder encodeur,
                             ServiceCourriel courriel, Clock horloge,
                             PolitiqueMotDePasse politique, LiensCourriel liens,
                             RevocationSessions sessions, JournalSecuriteCompte journal) {
        this.repository = repository;
        this.politique = politique;
        this.encodeur = encodeur;
        this.courriel = courriel;
        this.horloge = horloge;
        this.liens = liens;
        this.sessions = sessions;
        this.journal = journal;
    }

    /**
     * Traite une demande de reinitialisation.
     *
     * <p>Ne leve jamais d exception lorsque l adresse est inconnue : la reponse doit etre
     * indiscernable de celle d un compte existant, faute de quoi il deviendrait possible
     * d enumerer les membres de la plateforme.</p>
     */
    @Transactional
    public void demanderReinitialisation(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        repository.findByEmailIgnoreCase(email.trim().toLowerCase()).ifPresentOrElse(
                this::envoyerCourrielAdapte,
                () -> JOURNAL.info("Demande de reinitialisation pour une adresse inconnue."));
    }

    /**
     * Le contenu du courriel depend de l etat reel du compte. Une adresse non vérifiée
     * reçoit un rappel de vérification, avec un jeton de vérification de la durée
     * ordinaire : le suivre active le compte, il ne pose aucun mot de passe.
     */
    private void envoyerCourrielAdapte(Utilisateur membre) {
        JetonLien jeton = JetonLien.tirer();
        Instant maintenant = Instant.now(horloge);

        if (!membre.isEmailVerifie()) {
            membre.enregistrerJetonVerification(jeton.empreinte(),
                    maintenant.plus(InscriptionService.VALIDITE_JETON));
            courriel.envoyerRappelVerification(membre,
                    liens.vers("/inscription/verification?jeton=" + jeton.clair()));
        } else {
            membre.preparerReinitialisation(jeton.empreinte(), maintenant.plus(VALIDITE_JETON));
            courriel.envoyerReinitialisationMotDePasse(membre,
                    liens.vers("/mot-de-passe/nouveau?jeton=" + jeton.clair()));
        }
    }

    /** Verifie qu un jeton est exploitable avant d afficher le formulaire. */
    public void verifierJeton(String jeton) {
        chargerParJeton(jeton);
    }

    /**
     * Applique le nouveau mot de passe et libere le compte.
     *
     * <p>Toutes les sessions ouvertes du compte sont fermées : la réinitialisation est le
     * geste de qui a perdu la main sur son mot de passe, peut-être au profit d'un tiers
     * déjà connecté. Le titulaire en est averti par courriel, et le journal de sécurité
     * du compte en garde la trace.</p>
     *
     * @throws RessourceIntrouvableException si le jeton est inconnu
     * @throws RegleMetierException          si le jeton a expire ou si le mot de passe ne
     *                                       respecte pas {@link PolitiqueMotDePasse}
     */
    @Transactional
    public Utilisateur reinitialiser(String jeton, String nouveauMotDePasse) {
        Utilisateur membre = chargerParJeton(jeton);
        politique.exiger(nouveauMotDePasse);

        membre.reinitialiserMotDePasse(encodeur.encode(nouveauMotDePasse));
        journal.consigner(membre, TypeEvenementSecurite.MOT_DE_PASSE_REINITIALISE, membre);
        int fermees = sessions.revoquer(membre.getEmail());
        courriel.envoyerAvisMotDePasseModifie(membre, OrigineMotDePasse.REINITIALISATION);
        JOURNAL.info("Mot de passe reinitialise pour le compte {}, {} session(s) fermee(s)",
                membre.getReference(), fermees);
        return membre;
    }

    private Utilisateur chargerParJeton(String jeton) {
        Utilisateur membre = repository.findByJetonReinitialisationHache(JetonLien.empreinte(jeton))
                // Le jeton ne figure pas dans le message : il ouvrirait le compte a qui lit
                // les journaux d erreur.
                .orElseThrow(() -> new RessourceIntrouvableException("Lien de reinitialisation", "inconnu"));
        if (membre.jetonReinitialisationExpire(Instant.now(horloge))) {
            throw new RegleMetierException("RM-05", "Ce lien a expire. Demandez-en un nouveau.");
        }
        return membre;
    }
}
