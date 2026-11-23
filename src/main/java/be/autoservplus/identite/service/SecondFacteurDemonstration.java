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
 * Sème le second facteur du compte administrateur de démonstration, sous le profil
 * {@code demo} seulement.
 *
 * <p><b>Pourquoi un secret publié, et pas une exemption.</b> Le second facteur est
 * obligatoire au back-office. L'exempter sous un profil créerait un chemin de connexion
 * qui le contourne, et ce chemin existerait dans le binaire livre. Publier le secret du
 * compte de démonstration est de même nature que publier son mot de passe, ce que le
 * projet fait déjà, et proscrit déjà en production.</p>
 *
 * <p><b>Pourquoi Java et non SQL.</b> Le secret est chiffré sous la clé maître DU
 * DEPLOIEMENT, qu'une migration ne connaît pas : un chiffré figé en SQL ne se
 * déchiffrerait qu'avec la clé qui l'a produit.</p>
 *
 * <p><b>Posé s'il manque, et seulement alors.</b> Un compte déjà enrôlé n'est pas
 * touché : un évaluateur qui a enrôlé autre chose, ou un pas déjà consommé, n'a pas à
 * être réécrit à chaque démarrage. Le cas couvert est celui de la base restaurée depuis
 * le dump, dont l'admin n'a aucun secret.</p>
 *
 * <p><b>Un secret indechiffrable compte comme manquant.</b> C'est le cas après un
 * changement de {@code MFA_CLE_MAITRE} sans que l'ancienne ait été conservée dans
 * {@code MFA_CLES_PRECEDENTES} : sur un poste de démonstration, l'oubli le plus probable.
 * Le laisser enfermerait l'admin de démonstration hors du back-office, alors que le secret
 * publié au README est le seul qu'il puisse connaître.</p>
 */
@Component
@Profile("demo")
public class SecondFacteurDemonstration implements ApplicationRunner {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SecondFacteurDemonstration.class);

    static final String ADRESSE_ADMIN_DEMO = "admin@autoservplus.be";

    /** Publié au README. À saisir tel quel dans une application d'authentification. */
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
        utilisateurs.findByEmail(ADRESSE_ADMIN_DEMO)
                .filter(Utilisateur::estAdministrateur)
                .ifPresentOrElse(this::semerSiManquant,
                        () -> JOURNAL.warn("Compte {} absent : second facteur de demonstration "
                                + "non seme.", ADRESSE_ADMIN_DEMO));
    }

    private void semerSiManquant(Utilisateur admin) {
        if (secretUtilisable(admin)) {
            JOURNAL.info("Second facteur de demonstration deja en place pour {} : inchange.",
                    ADRESSE_ADMIN_DEMO);
            return;
        }
        semer(admin);
    }

    /** Actif ET dechiffrable sous les clés connues. Un enrôlement en attente ne compte pas. */
    private boolean secretUtilisable(Utilisateur admin) {
        if (!admin.secondFacteurActif()) {
            return false;
        }
        try {
            chiffrement.dechiffrer(admin.getMfaSecretChiffre(), admin.getMfaCleVersion(),
                    admin.getId());
            return true;
        } catch (ChiffrementSecretMfa.SecretIndechiffrableException ancienneCle) {
            return false;
        }
    }

    private void semer(Utilisateur admin) {

        ChiffrementSecretMfa.SecretChiffre chiffre =
                chiffrement.chiffrer(Base32.decoder(SECRET_PUBLIE), admin.getId());
        admin.retirerSecondFacteur();
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        // Pas zéro : tout code courant est accepté dès le premier essai après démarrage.
        admin.confirmerEnrolement(horloge.instant(), 0L);
        JOURNAL.warn("Second facteur de demonstration seme pour {} (secret publie au README). "
                + "Profil a ne jamais deployer.", ADRESSE_ADMIN_DEMO);
    }
}
