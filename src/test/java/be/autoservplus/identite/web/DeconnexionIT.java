package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Déconnexion par {@code POST /deconnexion}, sur une session ouverte par le vrai
 * formulaire de connexion.
 *
 * <p>Écrit avant de remplacer le sélecteur de la déconnexion dans {@code SecuriteConfig}
 * ({@code AntPathRequestMatcher}, déprécié) : il fige le comportement à garder. Un POST
 * ferme la session et renvoie à l'accueil. Un GET ne déconnecte pas : un lien, une image
 * ou une prélecture du navigateur ne doivent pas pouvoir fermer la session d'un membre.
 * Un POST sans jeton CSRF ne déconnecte pas davantage.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Déconnexion (intégration)")
class DeconnexionIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test de deconnexion";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private MockHttpSession session;

    @BeforeEach
    void connecter() throws Exception {
        Utilisateur membre = new Utilisateur("deconnexion-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        utilisateurs.saveAndFlush(membre);
        MvcResult connexion = mvc.perform(post("/connexion").with(csrf())
                .param("email", membre.getEmail()).param("password", MOT_DE_PASSE)).andReturn();
        session = (MockHttpSession) connexion.getRequest().getSession(false);
        assertThat(connecte(session)).as("la connexion de départ doit aboutir").isTrue();
    }

    private static boolean connecte(MockHttpSession session) {
        return session != null && !session.isInvalid() && session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null;
    }

    @Test
    @DisplayName("un POST avec jeton ferme la session, efface le cookie et renvoie à l'accueil")
    void postFermeLaSession() throws Exception {
        MvcResult resultat = mvc.perform(post("/deconnexion").session(session).with(csrf())).andReturn();

        assertThat(resultat.getResponse().getStatus()).isEqualTo(302);
        assertThat(resultat.getResponse().getRedirectedUrl()).isEqualTo("/?deconnecte");
        assertThat(session.isInvalid()).isTrue();
        var cookie = resultat.getResponse().getCookie("JSESSIONID");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("un GET ne déconnecte pas : 404, la session reste ouverte")
    void getNeDeconnectePas() throws Exception {
        MvcResult resultat = mvc.perform(get("/deconnexion").session(session)).andReturn();

        // Valeur relevée sur main à b354772, avant le changement de sélecteur.
        assertThat(resultat.getResponse().getStatus()).isEqualTo(404);
        assertThat(connecte(session)).isTrue();
        assertThat(mvc.perform(get("/mon-compte").session(session)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("un POST sans jeton CSRF ne déconnecte pas : 403, la session reste ouverte")
    void postSansJetonNeDeconnectePas() throws Exception {
        MvcResult resultat = mvc.perform(post("/deconnexion").session(session)).andReturn();

        assertThat(resultat.getResponse().getStatus()).isEqualTo(403);
        assertThat(connecte(session)).isTrue();
    }
}
