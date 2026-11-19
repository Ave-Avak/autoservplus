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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Plafond des échecs de connexion par IP, dans un contexte qui lui est propre.
 *
 * <p>Le plafond est ramené à cinq pour que les cas restent courts : c'est le même code
 * qu'à cinquante, dont {@code GardeConnexionTest} prouve la valeur. Le dernier cas tourne
 * sur un vrai serveur : la vanne RemoteIp de Tomcat, qui décide de croire ou non
 * X-Forwarded-For, n'existe pas sous MockMvc. Les proxys de confiance y sont réduits à une
 * adresse de documentation, si bien que le client local joue le rôle d'un visiteur qui
 * atteindrait l'application sans passer par le proxy.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "autoservplus.securite.connexion.echecs-max-par-ip=5",
        "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.250"
})
@AutoConfigureMockMvc
@DisplayName("Plafond des échecs de connexion par IP (integration)")
class PlafondConnexionIpIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du plafond par IP";
    private static final String REFUS = "/connexion?erreur";
    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final int PLAFOND = 5;

    @LocalServerPort private int port;
    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur membre() {
        Utilisateur u = new Utilisateur("plafond-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    private Utilisateur relire(Utilisateur u) {
        return utilisateurs.findById(u.getId()).orElseThrow();
    }

    private String connecter(String ip, String email, String motDePasse) throws Exception {
        return mvc.perform(post("/connexion").with(csrf())
                        .with(requete -> {
                            requete.setRemoteAddr(ip);
                            return requete;
                        })
                        .param("email", email).param("password", motDePasse))
                .andReturn().getResponse().getRedirectedUrl();
    }

    private void echecs(String ip, int nombre) throws Exception {
        for (int i = 0; i < nombre; i++) {
            assertThat(connecter(ip, "inconnu-" + i + "@exemple.be", "faux")).isEqualTo(REFUS);
        }
    }

    @Test
    @DisplayName("au plafond, même le bon mot de passe est refusé, sans rien écrire sur le compte")
    void auPlafond() throws Exception {
        Utilisateur membre = membre();
        echecs("203.0.113.10", PLAFOND);

        assertThat(connecter("203.0.113.10", membre.getEmail(), MOT_DE_PASSE)).isEqualTo(REFUS);
        assertThat(connecter("203.0.113.10", membre.getEmail(), "faux")).isEqualTo(REFUS);

        Utilisateur relu = relire(membre);
        assertThat(relu.getTentativesEchouees()).isZero();
        assertThat(relu.getDerniereConnexion()).isNull();
        assertThat(connecter("203.0.113.11", membre.getEmail(), MOT_DE_PASSE)).isNotEqualTo(REFUS);
    }

    @Test
    @DisplayName("un succès n'efface pas les échecs de l'IP")
    void succesNEffacePas() throws Exception {
        Utilisateur membre = membre();
        echecs("203.0.113.12", PLAFOND - 1);

        assertThat(connecter("203.0.113.12", membre.getEmail(), MOT_DE_PASSE)).isNotEqualTo(REFUS);
        echecs("203.0.113.12", 1);

        assertThat(connecter("203.0.113.12", membre.getEmail(), MOT_DE_PASSE)).isEqualTo(REFUS);
    }

    @Test
    @DisplayName("les adresses IPv6 d'un même /64 partagent le plafond")
    void ipv6() throws Exception {
        Utilisateur membre = membre();
        for (int i = 1; i <= PLAFOND; i++) {
            assertThat(connecter("2001:db8:1:2::" + i, "inconnu@exemple.be", "faux")).isEqualTo(REFUS);
        }

        assertThat(connecter("2001:db8:1:2::abcd", membre.getEmail(), MOT_DE_PASSE)).isEqualTo(REFUS);
        assertThat(connecter("2001:db8:1:3::1", membre.getEmail(), MOT_DE_PASSE)).isNotEqualTo(REFUS);
    }

    @Test
    @DisplayName("serveur réel : X-Forwarded-For d'un client hors des proxys de confiance est ignoré")
    void enTeteDUnClientIgnore() throws Exception {
        Utilisateur membre = membre();
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String jeton = jetonCsrf(client.send(HttpRequest.newBuilder(uri("/connexion")).GET().build(),
                HttpResponse.BodyHandlers.ofString()));

        for (int i = 0; i < PLAFOND; i++) {
            assertThat(envoyer(client, jeton, "198.51.100." + (i + 1), "inconnu@exemple.be", "faux"))
                    .endsWith(REFUS);
        }

        // Si l'en-tête était cru, cette IP neuve n'aurait aucun échec et la connexion passerait.
        assertThat(envoyer(client, jeton, "198.51.100.99", membre.getEmail(), MOT_DE_PASSE))
                .endsWith(REFUS);
        assertThat(relire(membre).getDerniereConnexion()).isNull();
    }

    /**
     * Le garde et le filtre qui authentifie doivent reconnaître exactement les mêmes
     * requêtes. Une variante du chemin que le second traiterait sans que le premier la
     * voie serait authentifiée sans plafond ni compte d'échec. Au plafond, aucune variante
     * ne doit donc ouvrir de session : elle est refusée par le pare-feu, ou n'est pas une
     * connexion, ou passe par le garde.
     */
    @Test
    @DisplayName("serveur réel : au plafond, aucune variante du chemin de connexion n'ouvre de session")
    void variantesDuChemin() throws Exception {
        Utilisateur membre = membre();
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String jeton = jetonCsrf(client.send(HttpRequest.newBuilder(uri("/connexion")).GET().build(),
                HttpResponse.BodyHandlers.ofString()));
        for (int i = 0; i < PLAFOND; i++) {
            envoyerSur(client, jeton, "/connexion", null, "inconnu@exemple.be", "faux");
        }

        for (String chemin : List.of("/connexion", "/%63onnexion", "/connexion/", "/Connexion")) {
            HttpResponse<String> reponse = envoyerSur(client, jeton, chemin, null,
                    membre.getEmail(), MOT_DE_PASSE);
            assertThat(reponse.headers().firstValue("Location").orElse(""))
                    .as("POST %s (statut %d)", chemin, reponse.statusCode())
                    .doesNotEndWith("/mon-compte");
            assertThat(relire(membre).getDerniereConnexion()).as("POST %s", chemin).isNull();
        }
    }

    private URI uri(String chemin) {
        return URI.create("http://localhost:" + port + chemin);
    }

    private String envoyer(HttpClient client, String jeton, String enTete, String email,
                           String motDePasse) throws Exception {
        return envoyerSur(client, jeton, "/connexion", enTete, email, motDePasse)
                .headers().firstValue("Location").orElseThrow();
    }

    private HttpResponse<String> envoyerSur(HttpClient client, String jeton, String chemin,
                                            String enTete, String email, String motDePasse)
            throws Exception {
        String corps = "_csrf=" + encoder(jeton) + "&email=" + encoder(email)
                + "&password=" + encoder(motDePasse);
        HttpRequest.Builder requete = HttpRequest.newBuilder(uri(chemin))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corps));
        if (enTete != null) {
            requete.header("X-Forwarded-For", enTete);
        }
        return client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encoder(String valeur) {
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
    }

    private static String jetonCsrf(HttpResponse<String> page) {
        Matcher jeton = JETON_CSRF.matcher(page.body());
        assertThat(jeton.find()).as("jeton CSRF dans la page").isTrue();
        return jeton.group(1);
    }
}
