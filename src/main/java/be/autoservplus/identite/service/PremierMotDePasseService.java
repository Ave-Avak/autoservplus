package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
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

/**
 * Le premier mot de passe que le titulaire choisit lui-meme : definition depuis le lien
 * recu a la creation d un compte administrateur, ou changement impose a la premiere
 * connexion du compte exploitant.
 *
 * <p>Deux chemins, un seul ecran : dans les deux cas, le secret en vigueur n a pas ete
 * choisi par la personne qui va s en servir. Seule change la preuve d identite : le
 * jeton du lien dans un cas, le mot de passe initial dans l autre.</p>
 */
@Service
@Transactional(readOnly = true)
public class PremierMotDePasseService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(PremierMotDePasseService.class);

    /** Une journee : le temps qu un nouvel administrateur ouvre sa boite, pas davantage. */
    public static final Duration VALIDITE_LIEN = Duration.ofHours(24);

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final JournalSecuriteCompte journal;
    private final Clock horloge;
    private final PolitiqueMotDePasse politique;

    public PremierMotDePasseService(UtilisateurRepository utilisateurs, PasswordEncoder encodeur,
                                    JournalSecuriteCompte journal, Clock horloge,
                                    PolitiqueMotDePasse politique) {
        this.utilisateurs = utilisateurs;
        this.politique = politique;
        this.encodeur = encodeur;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Verifie le jeton sans le consommer : un GET ne doit rien changer. */
    public void verifierJeton(String jeton) {
        chargerParJeton(jeton);
    }

    /**
     * Pose le mot de passe et consomme le jeton.
     *
     * @throws RessourceIntrouvableException jeton inconnu ou deja consomme, les deux cas
     *         sont indiscernables, et c est voulu : l ecran n a pas a dire qu un lien a
     *         existe
     * @throws RefusTraduit                  jeton expire, ou mot de passe hors
     *                                       {@link PolitiqueMotDePasse}
     */
    @Transactional
    public void definir(String jeton, String motDePasse) {
        Utilisateur compte = chargerParJeton(jeton);
        politique.exiger(motDePasse);
        compte.definirMotDePasse(encodeur.encode(motDePasse));
        journal.consigner(compte, TypeEvenementSecurite.MOT_DE_PASSE_DEFINI, compte);
        JOURNAL.info("Mot de passe defini par le titulaire du compte {}", compte.getReference());
    }

    /**
     * Changement impose a la premiere connexion. Refuse le mot de passe en vigueur : le
     * reprendre a l identique leverait l obligation sans rien changer au secret.
     */
    @Transactional
    public void changerImpose(String email, String motDePasse) {
        Utilisateur compte = utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
        politique.exiger(motDePasse);
        if (encodeur.matches(motDePasse, compte.getMotDePasseHache())) {
            throw new RefusTraduit("mot-de-passe-impose.erreur.identique",
                    "Choisissez un mot de passe different de celui qui vous a ete remis.");
        }
        compte.changerMotDePasse(encodeur.encode(motDePasse));
        journal.consigner(compte, TypeEvenementSecurite.MOT_DE_PASSE_CHANGE, compte);
        JOURNAL.info("Mot de passe impose change pour le compte {}", compte.getReference());
    }

    private Utilisateur chargerParJeton(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            throw new RessourceIntrouvableException("Lien de definition", "absent");
        }
        Utilisateur compte = utilisateurs.findByJetonDefinitionHache(JetonDefinition.empreinte(jeton))
                // Le jeton ne figure pas dans le message : il ouvrirait le compte a qui lit
                // les journaux d erreur.
                .orElseThrow(() -> new RessourceIntrouvableException("Lien de definition", "inconnu"));
        if (compte.jetonDefinitionExpire(horloge.instant())) {
            throw new RefusTraduit("definition.erreur.expire",
                    "Ce lien a expire. Demandez a un super-administrateur de vous le renvoyer.");
        }
        return compte;
    }
}
