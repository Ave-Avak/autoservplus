package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Inscription d un membre et verification de son adresse de courriel.
 *
 * <p>Couvre la fonctionnalite F1 du cahier des charges. Le compte reste au statut
 * EN_ATTENTE_VALIDATION tant que l adresse n a pas ete confirmee : cela evite qu une
 * inscription faite avec l adresse d autrui donne acces au service.</p>
 */
@Service
@Transactional(readOnly = true)
public class InscriptionService {

    /** Duree de validite du jeton de verification. */
    public static final Duration VALIDITE_JETON = Duration.ofHours(24);

    private static final int LONGUEUR_MINIMALE_MOT_DE_PASSE = 12;

    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeurMotDePasse;
    private final Clock horloge;
    private final SecureRandom aleatoire = new SecureRandom();

    public InscriptionService(UtilisateurRepository repository,
                              PasswordEncoder encodeurMotDePasse,
                              Clock horloge) {
        this.repository = repository;
        this.encodeurMotDePasse = encodeurMotDePasse;
        this.horloge = horloge;
    }

    /**
     * Inscrit un nouveau membre et genere son jeton de verification.
     *
     * @throws RegleMetierException si l adresse est deja utilisee ou si le mot de passe
     *                              ne respecte pas la longueur minimale
     */
    @Transactional
    public Utilisateur inscrire(String email, String motDePasseClair,
                                String nom, String prenom, Langue langue) {

        String emailNormalise = normaliser(email);

        if (repository.existsByEmailIgnoreCase(emailNormalise)) {
            throw new RegleMetierException("RM-01",
                    "Un compte existe deja pour l adresse %s.".formatted(emailNormalise));
        }
        if (motDePasseClair == null || motDePasseClair.length() < LONGUEUR_MINIMALE_MOT_DE_PASSE) {
            throw new RegleMetierException("RM-02",
                    "Le mot de passe doit comporter au moins %d caracteres."
                            .formatted(LONGUEUR_MINIMALE_MOT_DE_PASSE));
        }

        Utilisateur membre = new Utilisateur(
                emailNormalise,
                encodeurMotDePasse.encode(motDePasseClair),
                nom.trim(),
                prenom.trim(),
                TypeUtilisateur.MEMBRE);

        membre.setLangue(langue == null ? Langue.fr : langue);
        membre.enregistrerJetonVerification(genererJeton(), Instant.now(horloge).plus(VALIDITE_JETON));

        return repository.save(membre);
    }

    /**
     * Confirme une adresse de courriel a partir de son jeton et active le compte.
     *
     * @throws RessourceIntrouvableException si le jeton n existe pas
     * @throws RegleMetierException          si le jeton a expire
     */
    @Transactional
    public Utilisateur confirmerAdresse(String jeton) {
        Utilisateur membre = repository.findByJetonVerification(jeton)
                .orElseThrow(() -> new RessourceIntrouvableException("Jeton de verification", jeton));

        if (membre.jetonEstExpire(Instant.now(horloge))) {
            throw new RegleMetierException("RM-03",
                    "Le lien de verification a expire. Demandez un nouvel envoi.");
        }

        membre.confirmerAdresseEmail();
        return membre;
    }

    /** Regenere un jeton pour un compte dont le lien precedent a expire. */
    @Transactional
    public Utilisateur renvoyerVerification(String email) {
        Utilisateur membre = repository.findByEmailIgnoreCase(normaliser(email))
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));

        if (membre.isEmailVerifie()) {
            throw new RegleMetierException("RM-04",
                    "Cette adresse est deja verifiee.");
        }

        membre.enregistrerJetonVerification(genererJeton(), Instant.now(horloge).plus(VALIDITE_JETON));
        return membre;
    }

    private String normaliser(String email) {
        if (email == null || email.isBlank()) {
            throw new RegleMetierException("RM-01", "L adresse de courriel est obligatoire.");
        }
        return email.trim().toLowerCase();
    }

    /** Jeton aleatoire de 256 bits, represente en hexadecimal sur 64 caracteres. */
    private String genererJeton() {
        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }
}