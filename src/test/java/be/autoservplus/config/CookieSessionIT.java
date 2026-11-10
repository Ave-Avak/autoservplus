package be.autoservplus.config;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attributs du cookie de session, tels que le serveur les émet.
 *
 * <p>Serveur réel et non MockMvc : {@code SameSite} et {@code HttpOnly} sont posés par le
 * Tomcat embarqué à partir de {@code server.servlet.session.cookie}, et MockMvc ne passe
 * pas par lui. Un test MockMvc ne verrait aucun cookie, ou un cookie sans ces
 * attributs.</p>
 *
 * <p><b>{@code SameSite=Lax} en premier.</b> La valeur n'existe que pour le retour de
 * paiement : avec {@code Strict}, le navigateur retient le cookie sur la redirection
 * venue de chez Mollie, la session est perdue et le membre retrouve un écran de connexion
 * au lieu de sa commande payée. Jusqu'ici, rien ne cassait si quelqu'un passait la valeur
 * à {@code Strict} : le commentaire d'{@code application.yml} était tout ce qui la
 * défendait.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Cookie de session (serveur réel)")
class CookieSessionIT extends SocleIntegration {

    @LocalServerPort private int port;

    /** Le cookie de session posé par la page de connexion, qui ouvre une session. */
    private String cookieDeSession() throws Exception {
        HttpResponse<String> reponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/connexion"))
                        .header("Accept", "text/html").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<String> cookies = reponse.headers().allValues("Set-Cookie");
        return cookies.stream().filter(c -> c.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun cookie de session parmi " + cookies));
    }

    @Test
    @DisplayName("SameSite=Lax, ni Strict ni None : le retour de paiement garde la session")
    void sameSiteLax() throws Exception {
        assertThat(cookieDeSession())
                .containsIgnoringCase("SameSite=Lax")
                .doesNotContainIgnoringCase("SameSite=Strict")
                .doesNotContainIgnoringCase("SameSite=None");
    }

    @Test
    @DisplayName("HttpOnly : aucun script ne lit l'identifiant de session")
    void httpOnly() throws Exception {
        assertThat(cookieDeSession()).containsIgnoringCase("HttpOnly");
    }
}
