package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.Base32;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Seme le second facteur du compte administrateur de demonstration, sous le profil
 * {@code demo} seulement.
 *
 * <p><b>Pourquoi un secret publie, et pas une exemption.</b> Le second facteur est
 * obligatoire au back-office ; l exempter sous un profil creerait un chemin de connexion
 * qui le contourne, et ce chemin existerait dans le binaire livre. Publier le secret du
 * compte de demonstration est de meme nature que publier son mot de passe — ce que le
 * projet fait deja, et proscrit deja en production.</p>
 *
 * <p><b>Pourquoi Java et non SQL.</b> Le secret est chiffre sous la cle maitre DU
 * DEPLOIEMENT, qu une migration ne connait pas : un chiffre fige en SQL ne se
 * dechiffrerait qu avec la cle qui l a produit. Il est donc reecrit a chaque demarrage,
 * sous la cle du moment — ce qui le rend aussi insensible a une rotation de cle qu a la
 * cle ephemere du developpement.</p>
 */
@Component
@Profile("demo")
public class SecondFacteurDemonstration implements ApplicationRunner {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SecondFacteurDemonstration.class);

    static final String ADRESSE_ADMIN_DEMO = "admin@autoservplus.be";

    /** Publie au README. A saisir tel quel dans une application d authentification. */
    public static final String SECRET_PUBLIE = "IF2XI32TMVZHMK2EMVWW6LKUJ5KFAMRW";

    private final UtilisateurRepository utilisateurs;
    private final ChiffrementSecretMfa chiffrement;
    private final Clock horloge;

    public SecondFacteurDemonstration(UtilisateurRepository utilisateurs,
                                      ChiffrementSecretMfa chiffrement, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.chiffrement = chiffrement;
        this.horloge = horloge;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        utilisateurs.findByEmailIgnoreCase(ADRESSE_ADMIN_DEMO)
                .filter(Utilisateur::estAdministrateur)
                .ifPresentOrElse(this::semer,
                        () -> JOURNAL.warn("Compte {} absent : second facteur de demonstration "
                                + "non seme.", ADRESSE_ADMIN_DEMO));
    }

    private void semer(Utilisateur admin) {
        ChiffrementSecretMfa.SecretChiffre chiffre =
                chiffrement.chiffrer(Base32.decoder(SECRET_PUBLIE), admin.getId());
        admin.retirerSecondFacteur();
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        // Pas zero : tout code courant est accepte des le premier essai apres demarrage.
        admin.confirmerEnrolement(horloge.instant(), 0L);
        JOURNAL.warn("Second facteur de demonstration seme pour {} (secret publie au README). "
                + "Profil a ne jamais deployer.", ADRESSE_ADMIN_DEMO);
    }
}
