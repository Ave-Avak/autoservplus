package be.autoservplus.cookies.web;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Première visite, sans session ni cookie, sur une page plus longue que le tampon de
 * réponse du conteneur.
 *
 * <p>Le bandeau des cookies est un formulaire en fin de page. Son jeton CSRF crée la
 * session à sa première lecture, et le conteneur refuse de créer une session une fois
 * la réponse partie : au-delà de 8 ko avant le bandeau, la page sortait tronquée, en
 * 200, sans bandeau ni {@code </html>}. Le test démarre un vrai serveur : MockMvc
 * n'envoie rien avant la fin du rendu et ne connaît pas ce tampon. Chaque appel part
 * d'un client neuf, sans cookie, ce qu'un navigateur de recette n'est presque jamais.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Première visite sans session (serveur réel)")
class PremiereVisiteSansSessionIT extends SocleIntegration {

    /** Tampon de réponse par défaut de Tomcat, au-delà duquel la réponse part. */
    private static final int TAMPON = 8192;
    private static final String BANDEAU = "class=\"bandeau-cookies\"";

    @LocalServerPort private int port;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/services", "/cgv", "/confidentialite", "/contact"})
    @DisplayName("la page arrive entière, bandeau des cookies compris")
    void pageEntiere(String adresse) throws Exception {
        HttpResponse<String> reponse;
        try {
            reponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + adresse))
                            .header("Accept", "text/html").header("Accept-Language", "fr").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException coupure) {
            // Le conteneur ferme la connexion au milieu de l'envoi : le client ne reçoit
            // pas la fin du corps annoncé par morceaux.
            throw new AssertionError("réponse coupée avant la fin sur " + adresse, coupure);
        }
        String page = reponse.body();

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(page).as("bandeau des cookies sur " + adresse).contains(BANDEAU, "name=\"_csrf\"");
        assertThat(page.stripTrailing()).as("fin du document sur " + adresse).endsWith("</html>");
        assertThat(page).as("identifiant de session dans une adresse sur " + adresse).doesNotContain("jsessionid");
        // Sans cette condition, une page raccourcie passerait sous le tampon et le test
        // ne traverserait plus le cas qu'il garde.
        int avantLeBandeau = page.substring(0, page.indexOf(BANDEAU)).getBytes(StandardCharsets.UTF_8).length;
        assertThat(avantLeBandeau).as("octets envoyés avant le bandeau sur " + adresse).isGreaterThan(TAMPON);
    }
}
