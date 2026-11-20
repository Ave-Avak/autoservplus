package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.OrigineMotDePasse;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changement du mot de passe par le titulaire, depuis son profil.
 *
 * <p>Le mot de passe actuel est exigé : une session laissée ouverte ou volée ne suffit
 * pas à changer le secret, ce qui fermerait la porte au titulaire. Le nouveau passe la
 * même politique qu'à l'inscription et à la réinitialisation (longueur, liste des mots
 * de passe fréquents, service de compromission).</p>
 *
 * <p>Au succès, les autres sessions du compte sont fermées, comme à l'activation du
 * second facteur : un tiers déjà connecté ailleurs est évincé, et le titulaire reste
 * connecté là où il agit. Le contrôleur renouvelle ensuite l'identifiant de cette
 * session (Stratégie, A07, sixième contrôle).</p>
 */
@Service
@PreAuthorize("isAuthenticated()")
public class ChangementMotDePasseService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ChangementMotDePasseService.class);

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final PolitiqueMotDePasse politique;
    private final JournalSecuriteCompte journal;
    private final RevocationSessions revocation;
    private final ServiceCourriel courriel;

    public ChangementMotDePasseService(UtilisateurRepository utilisateurs, PasswordEncoder encodeur,
                                       PolitiqueMotDePasse politique, JournalSecuriteCompte journal,
                                       RevocationSessions revocation, ServiceCourriel courriel) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.politique = politique;
        this.journal = journal;
        this.revocation = revocation;
        this.courriel = courriel;
    }

    /**
     * @param idSessionConservee la session d'où le titulaire agit, seule laissée ouverte
     * @throws MotDePasseActuelIncorrectException le mot de passe actuel ne correspond pas
     * @throws RefusTraduit                       nouveau mot de passe hors politique, ou
     *                                            identique à l'actuel
     */
    @Transactional
    public void changer(String email, String actuel, String nouveau, String idSessionConservee) {
        Utilisateur compte = charger(email);
        if (actuel == null || actuel.isEmpty()
                || !encodeur.matches(actuel, compte.getMotDePasseHache())) {
            throw new MotDePasseActuelIncorrectException();
        }
        politique.exiger(nouveau);
        if (encodeur.matches(nouveau, compte.getMotDePasseHache())) {
            throw new RefusTraduit("mot-de-passe-changement.erreur.identique",
                    "Choisissez un mot de passe différent de l'actuel.");
        }
        compte.changerMotDePasse(encodeur.encode(nouveau));
        journal.consigner(compte, TypeEvenementSecurite.MOT_DE_PASSE_CHANGE, compte);
        int fermees = revocation.revoquerSauf(compte.getEmail(), idSessionConservee);
        courriel.envoyerAvisMotDePasseModifie(compte, OrigineMotDePasse.CHANGEMENT_PROFIL);
        JOURNAL.info("Mot de passe change depuis le profil pour le compte {}, {} autre(s) session(s) fermee(s)",
                compte.getReference(), fermees);
    }

    /**
     * Cinquième mot de passe actuel incorrect dans la session : le contrôleur la ferme,
     * le journal du compte le garde. Le compte n'est pas verrouillé, le titulaire n'a
     * qu'à se reconnecter.
     */
    @Transactional
    public void consignerBlocage(String email) {
        Utilisateur compte = charger(email);
        // Auteur nul : le système constate les échecs sans savoir qui saisissait, comme
        // pour le verrouillage à la connexion. Le titulaire n'en est pas l'auteur présumé.
        journal.consigner(compte, TypeEvenementSecurite.CHANGEMENT_MOT_DE_PASSE_BLOQUE, null);
        JOURNAL.warn("Session fermee apres des mots de passe actuels incorrects pour le compte {}",
                compte.getReference());
    }

    private Utilisateur charger(String email) {
        return utilisateurs.findByEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }
}
