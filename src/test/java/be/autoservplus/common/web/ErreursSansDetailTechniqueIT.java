package be.autoservplus.common.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucune reponse d erreur ne porte de detail technique ni la page Whitelabel, en HTML
 * comme en JSON, sur un vrai serveur.
 *
 * <p>Le test demarre un vrai serveur. Une exception non rattrapee repasse par le
 * conteneur ({@code sendError}, puis dispatch {@code ERROR} vers {@code /error}) ;
 * MockMvc ne fait jamais ce second passage, c est donc la que la trace sortait sans
 * qu aucun test la voie.</p>
 *
 * <p>Il reprend les valeurs de devtools. Hors devtools, Spring Boot ne revele deja
 * rien : un test sans elles passerait avec ou sans le reglage d {@code application.yml}.
 * L initialiseur les inscrit en derniere position, comme devtools : le test echoue des
 * que {@code server.error.*} cesse d etre pose explicitement.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = ErreursSansDetailTechniqueIT.CommeDevtools.class)
@DisplayName("Erreurs sans détail technique (serveur réel)")
class ErreursSansDetailTechniqueIT extends SocleIntegration {

    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String MOT_DE_PASSE = "phrase de passe du test des erreurs techniques";
    private static final String[] INTERDITS = {"Whitelabel", "Exception", "at org."};

    /** Les valeurs que devtools pose en developpement, au rang le plus faible. */
    static class CommeDevtools implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext contexte) {
            contexte.getEnvironment().getPropertySources().addLast(new MapPropertySource("comme-devtools", Map.of(
                    "server.error.include-stacktrace", "always",
                    "server.error.include-message", "always",
                    "server.error.include-binding-errors", "always")));
        }
    }

    @LocalServerPort private int port;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> lire(String chemin, String accept, String langue) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                        .header("Accept", accept).header("Accept-Language", langue).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void connecterUnMembre() throws Exception {
        Utilisateur membre = new Utilisateur("erreurs-techniques@exemple.be", encodeur.encode(MOT_DE_PASSE),
                "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        utilisateurs.saveAndFlush(membre);

        Matcher jeton = JETON_CSRF.matcher(lire("/connexion", "text/html", "fr").body());
        assertThat(jeton.find()).isTrue();
        String corps = "_csrf=" + URLEncoder.encode(jeton.group(1), StandardCharsets.UTF_8)
                + "&email=erreurs-techniques%40exemple.be&password="
                + URLEncoder.encode(MOT_DE_PASSE, StandardCharsets.UTF_8);
        HttpResponse<String> connexion = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/connexion"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(corps)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(connexion.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/mon-compte"));
    }

    /** La page traduite en HTML, et la reponse JSON du meme chemin sans aucun detail. */
    private void verifier(String chemin, int statut, String langue, String titre) throws Exception {
        HttpResponse<String> page = lire(chemin, "text/html", langue);
        assertThat(page.statusCode()).as(chemin).isEqualTo(statut);
        assertThat(page.body()).as(chemin).contains("<h1>" + titre + "</h1>").doesNotContain(INTERDITS);

        HttpResponse<String> json = lire(chemin, "application/json", langue);
        assertThat(json.statusCode()).as(chemin + " en JSON").isEqualTo(statut);
        assertThat(json.body()).as(chemin + " en JSON").doesNotContain(INTERDITS).doesNotContain("\"trace\"", "\"message\"");
    }

    @Test
    @DisplayName("membre connecté, adresse inconnue : 404 traduite")
    void adresseInconnueConnecte() throws Exception {
        connecterUnMembre();
        verifier("/nimporte-quoi", 404, "fr", "Page introuvable");
    }

    @Test
    @DisplayName("ressource statique inconnue : 404 traduite, en français et en néerlandais")
    void ressourceStatiqueInconnue() throws Exception {
        verifier("/css/inexistante.css", 404, "fr", "Page introuvable");
        verifier("/css/inexistante.css", 404, "nl", "Pagina niet gevonden");
    }

    @Test
    @DisplayName("identifiant malformé : 400 traduite, en français et en néerlandais")
    void identifiantMalforme() throws Exception {
        verifier("/pieces/pas-un-identifiant", 400, "fr", "Demande impossible");
        verifier("/pieces/pas-un-identifiant", 400, "nl", "Verzoek niet mogelijk");
    }
}
