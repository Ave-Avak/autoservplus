package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Vérification humaine à la connexion, après trois échecs sur l'IP ou sur l'adresse.
 *
 * <p>Clés de test publiées par Cloudflare, comme {@code TurnstileIT}. Aucun cas ne dépend
 * du réseau : le jeton absent est refusé sans appel à Cloudflare, et c'est ce refus qui est
 * prouvé. Les cas de la classe partagent son contexte et donc ses compteurs : chacun emploie
 * ses propres IP et ses propres adresses, si bien que leur ordre ne change rien.</p>
 */
@SpringBootTest(properties = {
        "autoservplus.securite.turnstile.cle-site=1x00000000000000000000AA",
        "autoservplus.securite.turnstile.cle-secrete=1x0000000000000000000000000000000AA"})
@AutoConfigureMockMvc
@DisplayName("Turnstile à la connexion (integration)")
class TurnstileConnexionIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test turnstile";
    private static final String REFUS = "/connexion?erreur";
    private static final String WIDGET = "cf-turnstile";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur membre() {
        Utilisateur u = new Utilisateur("turnstile-connexion-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    private static RequestPostProcessor depuis(String ip) {
        return requete -> {
            requete.setRemoteAddr(ip);
            return requete;
        };
    }

    private MvcResult connecter(String ip, String email, String motDePasse) throws Exception {
        return mvc.perform(post("/connexion").with(csrf()).with(depuis(ip))
                .param("email", email).param("password", motDePasse)).andReturn();
    }

    private String page(String ip, MvcResult tentative) throws Exception {
        var requete = get("/connexion").param("erreur", "").with(depuis(ip));
        MockHttpSession session = tentative == null ? null
                : (MockHttpSession) tentative.getRequest().getSession(false);
        if (session != null) {
            requete.session(session);
        }
        return mvc.perform(requete).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("sous le seuil : pas de widget, et la connexion passe sans jeton")
    void sousLeSeuil() throws Exception {
        Utilisateur membre = membre();

        assertThat(page("203.0.113.21", null)).doesNotContain(WIDGET);
        assertThat(connecter("203.0.113.21", membre.getEmail(), MOT_DE_PASSE)
                .getResponse().getRedirectedUrl()).isNotEqualTo(REFUS);
    }

    @Test
    @DisplayName("trois échecs depuis une IP : widget affiché, bon mot de passe sans jeton refusé sans rien écrire")
    void troisEchecsDepuisUneIp() throws Exception {
        Utilisateur membre = membre();
        for (int i = 0; i < 3; i++) {
            connecter("203.0.113.22", "personne-" + i + "@exemple.be", "faux");
        }

        assertThat(page("203.0.113.22", null)).contains(WIDGET);
        MvcResult sansJeton = connecter("203.0.113.22", membre.getEmail(), MOT_DE_PASSE);

        assertThat(sansJeton.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        Utilisateur relu = utilisateurs.findById(membre.getId()).orElseThrow();
        assertThat(relu.getDerniereConnexion()).isNull();
        assertThat(relu.getTentativesEchouees()).isZero();
    }

    @Test
    @DisplayName("trois échecs sur une adresse, connue ou non : même refus et même widget depuis une IP neuve")
    void troisEchecsSurUneAdresse() throws Exception {
        Utilisateur membre = membre();
        String inconnue = "jamais-inscrit-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        for (int i = 0; i < 3; i++) {
            connecter("203.0.113." + (30 + i), membre.getEmail(), "faux");
            connecter("203.0.113." + (40 + i), inconnue, "faux");
        }

        MvcResult connue = connecter("203.0.113.50", membre.getEmail(), MOT_DE_PASSE);
        MvcResult nonInscrite = connecter("203.0.113.51", inconnue, MOT_DE_PASSE);

        assertThat(connue.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        assertThat(nonInscrite.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        assertThat(page("203.0.113.50", connue)).contains(WIDGET);
        assertThat(page("203.0.113.51", nonInscrite)).contains(WIDGET);
        assertThat(utilisateurs.findById(membre.getId()).orElseThrow().getDerniereConnexion()).isNull();
    }

    @Test
    @DisplayName("la politique de sécurité du contenu de la page de connexion admet le widget")
    void politiqueDeContenu() throws Exception {
        String politique = mvc.perform(get("/connexion").with(depuis("203.0.113.60")))
                .andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(directive(politique, "script-src")).contains("https://challenges.cloudflare.com");
        assertThat(directive(politique, "frame-src")).contains("https://challenges.cloudflare.com");
    }

    private static String directive(String politique, String nom) {
        return Arrays.stream(politique.split(";")).map(String::strip)
                .filter(d -> d.startsWith(nom + " ")).findFirst().orElse("");
    }
}
