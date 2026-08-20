package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Reinitialisation du mot de passe.
 *
 * <p>Le service ne revele jamais si une adresse correspond a un compte : la reponse est
 * toujours identique. L utilisateur obtient l information par le seul canal legitime,
 * sa boite de reception. Le contenu du courriel s adapte a la situation reelle du
 * compte, ce qui evite qu un membre n ayant jamais active son inscription ne reste
 * bloque sans comprendre pourquoi.</p>
 */
@Service
@Transactional(readOnly = true)
public class MotDePasseService {

    /** Duree volontairement courte : ce jeton donne acces au compte. */
    public static final Duration VALIDITE_JETON = Duration.ofHours(1);

    private static final int LONGUEUR_MINIMALE = 12;
    private static final Logger JOURNAL = LoggerFactory.getLogger(MotDePasseService.class);

    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeur;
    private final ServiceCourriel courriel;
    private final Clock horloge;
    private final SecureRandom aleatoire = new SecureRandom();

    public MotDePasseService(UtilisateurRepository repository, PasswordEncoder encodeur,
                             ServiceCourriel courriel, Clock horloge) {
        this.repository = repository;
        this.encodeur = encodeur;
        this.courriel = courriel;
        this.horloge = horloge;
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

    /** Le contenu du courriel depend de l etat reel du compte. */
    private void envoyerCourrielAdapte(Utilisateur membre) {
        String jeton = genererJeton();
        membre.enregistrerJetonVerification(jeton, Instant.now(horloge).plus(VALIDITE_JETON));

        if (membre.getStatut() == StatutUtilisateur.EN_ATTENTE_VALIDATION) {
            courriel.envoyerRappelVerification(membre,
                    "/inscription/verification?jeton=" + jeton);
        } else {
            courriel.envoyerReinitialisationMotDePasse(membre,
                    "/mot-de-passe/nouveau?jeton=" + jeton);
        }
    }

    /** Verifie qu un jeton est exploitable avant d afficher le formulaire. */
    public void verifierJeton(String jeton) {
        Utilisateur membre = repository.findByJetonVerification(jeton)
                .orElseThrow(() -> new RessourceIntrouvableException("Lien de reinitialisation", jeton));
        if (membre.jetonEstExpire(Instant.now(horloge))) {
            throw new RegleMetierException("RM-05",
                    "Ce lien a expire. Demandez-en un nouveau.");
        }
    }

    /**
     * Applique le nouveau mot de passe et libere le compte.
     *
     * @throws RessourceIntrouvableException si le jeton est inconnu
     * @throws RegleMetierException          si le jeton a expire ou le mot de passe est trop court
     */
    @Transactional
    public Utilisateur reinitialiser(String jeton, String nouveauMotDePasse) {
        Utilisateur membre = repository.findByJetonVerification(jeton)
                .orElseThrow(() -> new RessourceIntrouvableException("Lien de reinitialisation", jeton));

        if (membre.jetonEstExpire(Instant.now(horloge))) {
            throw new RegleMetierException("RM-05", "Ce lien a expire. Demandez-en un nouveau.");
        }
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < LONGUEUR_MINIMALE) {
            throw new RegleMetierException("RM-02",
                    "Le mot de passe doit comporter au moins %d caracteres.".formatted(LONGUEUR_MINIMALE));
        }

        membre.reinitialiserMotDePasse(encodeur.encode(nouveauMotDePasse));
        JOURNAL.info("Mot de passe reinitialise pour le compte {}", membre.getReference());
        return membre;
    }

    private String genererJeton() {
        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }
}