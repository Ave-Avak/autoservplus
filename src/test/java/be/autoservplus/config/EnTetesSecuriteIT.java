package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * En-tetes de securite reellement emis sur une reponse servie.
 *
 * <p>Test d <b>integration</b> et non {@code @WebMvcTest} : ce qui doit etre prouve
 * n est pas que {@code SecuriteConfig} declare une politique, mais qu un navigateur
 * la recoit. Un test a doublures instancie sa propre chaine de filtres — celle des
 * {@code SecuriteTest} qui parsement les {@code @WebMvcTest} du projet — et resterait
 * vert quelle que soit la politique reelle.</p>
 *
 * <p><b>Pourquoi {@code form-action} merite un test a lui seul.</b> Le depart vers le
 * paiement est un POST dont la reponse redirige vers Mollie ; Chrome et Edge
 * appliquent {@code form-action} a la cible de cette redirection, Firefox non. Le
 * defaut ne se manifeste donc ni dans {@code verify}, ni sur la passerelle
 * bouchonnee — qui redirige vers une page interne, couverte par {@code 'self'} — ni
 * meme sur un navigateur. Il a fallu une repetition de deploiement avec le vrai
 * prestataire, menee depuis Chrome, pour le voir. A defaut de pouvoir rejouer cela
 * dans une suite de tests, c est la politique elle-meme qui est verrouillee ici.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("En-tetes de securite (integration)")
class EnTetesSecuriteIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("form-action autorise le site et le seul hote du prestataire de paiement")
    void formActionAutoriseMollie() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        containsString("form-action 'self' https://www.mollie.com")));
    }

    @Test
    @DisplayName("aucune autre directive n est relachee au passage")
    void resteDeLaPolitiqueInchange() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        // HTMX est servi en local : aucun CDN n a jamais ete autorise, et
                        // l assouplissement de form-action ne doit pas servir de precedent.
                        containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("base-uri 'self'")))
                // Un joker sur form-action rendrait le test precedent vert tout en
                // autorisant l envoi d un formulaire vers n importe quel tiers.
                .andExpect(header().string("Content-Security-Policy", not(containsString("*"))));
    }

    @Test
    @DisplayName("les autres en-tetes de securite sont poses")
    void autresEntetes() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }
}
