package be.autoservplus.rgpd.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suppression de compte par le chemin HTTP (F23) : ce que seule la couche web peut
 * prouver — l impossibilite de viser le compte d autrui, le refus sans confirmation,
 * et la revocation effective de la session.
 *
 * <p>Separe de {@code SuppressionCompteIT}, qui exerce le service : celui-ci doit
 * pouvoir compiler et passer sans controleur ni configuration de securite, pour que
 * le commit qui livre l anonymisation porte lui-meme sa preuve.</p>
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} de classe : la revocation de
 * session et l invalidation cote serveur ne se constatent qu apres un vrai cycle de
 * requete. Consequence : les donnees restent en base du conteneur, d ou les adresses
 * uniques par test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Suppression de compte par le web (integration)")
class SuppressionCompteWebIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "MonMotDePasse2026!";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;

    /** Membre neuf, adresse unique : la classe ne rollbacke pas. */
    private Utilisateur membre() {
        String email = "web-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur nouveau = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            nouveau.confirmerAdresseEmail();
            return utilisateurs.saveAndFlush(nouveau);
        });
    }

    private Utilisateur recharger(Long id) {
        return transactions.execute(statut -> utilisateurs.findById(id).orElseThrow());
    }

    @Test
    @DisplayName("un membre ne peut pas viser le compte d'un autre")
    void pasDeSuppressionDAutrui() throws Exception {
        Utilisateur marie = membre();
        Utilisateur jean = membre();

        // Il n existe aucun parametre d URL designant un titulaire : le POST ne
        // porte que le mot de passe et la confirmation, l identite vient du
        // contexte. Jean ne peut donc supprimer que son propre compte, et son mot
        // de passe ne deverrouille pas celui de Marie.
        mvc.perform(post("/supprimer-mon-compte").with(user(jean.getEmail())).with(csrf())
                        .param("motDePasse", MOT_DE_PASSE)
                        .param("confirmation", "SUPPRIMER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compte-supprime"));

        assertThat(recharger(marie.getId()).estAnonymise()).isFalse();
        assertThat(recharger(jean.getId()).estAnonymise()).isTrue();
    }

    @Test
    @DisplayName("le POST refuse sans confirmation et ne touche rien")
    void refusSansConfirmation() throws Exception {
        Utilisateur marie = membre();

        mvc.perform(post("/supprimer-mon-compte").with(user(marie.getEmail())).with(csrf())
                        .param("motDePasse", MOT_DE_PASSE))
                .andExpect(redirectedUrl("/supprimer-mon-compte?erreur=confirmation"));

        assertThat(recharger(marie.getId()).estAnonymise()).isFalse();
    }

    @Test
    @DisplayName("le POST refuse sur un mauvais mot de passe et ne touche rien")
    void refusMotDePasse() throws Exception {
        Utilisateur marie = membre();

        mvc.perform(post("/supprimer-mon-compte").with(user(marie.getEmail())).with(csrf())
                        .param("motDePasse", "mauvais")
                        .param("confirmation", "SUPPRIMER"))
                .andExpect(redirectedUrl("/supprimer-mon-compte?erreur=motdepasse"));

        assertThat(recharger(marie.getId()).estAnonymise()).isFalse();
    }

    @Test
    @DisplayName("la session est invalidee : l'acces est revoque avec le compte")
    void sessionRevoquee() throws Exception {
        Utilisateur marie = membre();

        HttpSession session = mvc.perform(post("/supprimer-mon-compte")
                        .with(user(marie.getEmail())).with(csrf())
                        .param("motDePasse", MOT_DE_PASSE)
                        .param("confirmation", "SUPPRIMER"))
                .andExpect(redirectedUrl("/compte-supprime"))
                .andReturn().getRequest().getSession(false);

        // SecurityContextLogoutHandler invalide la session cote serveur. Sans cela,
        // l onglet reste ouvert sur un compte suppose efface — un acces vivant sur
        // une identite qui n existe plus.
        assertThat(session == null || !estValide(session))
                .as("La session doit etre morte apres la suppression")
                .isTrue();
    }

    private static boolean estValide(HttpSession session) {
        try {
            session.getAttributeNames();
            return true;
        } catch (IllegalStateException deja) {
            return false;
        }
    }
}
