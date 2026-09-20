package be.autoservplus.identite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verdict de Cloudflare et comportement en panne, sur serveur simule.
 *
 * <p>Ce que le test d integration ne peut pas montrer : avec les cles de test, la
 * frontiere rend le meme resultat que Cloudflare reponde ou non — elle accepte dans
 * les deux cas. Seul un serveur simule permet de distinguer « Cloudflare a valide » de
 * « Cloudflare n a pas repondu ».</p>
 */
@DisplayName("Verification Turnstile")
class VerificateurTurnstileTest {

    private static final String SECRET = "1x0000000000000000000000000000000AA";
    private static final String JETON = "XXXX.DUMMY.TOKEN.XXXX";

    private MockRestServiceServer serveur;
    private VerificateurTurnstile verificateur;

    private void avecServeur() {
        RestClient.Builder constructeur = RestClient.builder()
                .baseUrl("https://challenges.cloudflare.com/turnstile/v0/siteverify");
        serveur = MockRestServiceServer.bindTo(constructeur).build();
        verificateur = new VerificateurTurnstile(constructeur.build(), SECRET);
    }

    @Test
    @DisplayName("réponse success=true : jeton accepté")
    void succes() {
        avecServeur();
        serveur.expect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThat(verificateur.jetonValide(JETON, "203.0.113.9")).isTrue();
    }

    /**
     * Le cas qui compte : un jeton refuse doit etre refuse. Sans ce test, une
     * implementation qui accepterait tout passerait tous les autres.
     */
    @Test
    @DisplayName("réponse success=false : jeton refusé")
    void refus() {
        avecServeur();
        serveur.expect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));

        assertThat(verificateur.jetonValide(JETON, null)).isFalse();
    }

    @Test
    @DisplayName("le secret et le jeton sont transmis, l'IP aussi quand elle est connue")
    void corpsDeLaRequete() {
        avecServeur();
        serveur.expect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("secret=" + SECRET),
                        org.hamcrest.Matchers.containsString("remoteip=203.0.113.9"))))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        verificateur.jetonValide(JETON, "203.0.113.9");

        serveur.verify();
    }

    /**
     * Une panne de Cloudflare ne bloque pas l inscription : les trois autres couches
     * restent en vigueur, et lui confier la disponibilite du site serait un choix,
     * pas une consequence.
     */
    @Test
    @DisplayName("Cloudflare injoignable : la soumission passe")
    void panneAccepte() {
        avecServeur();
        serveur.expect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(verificateur.jetonValide(JETON, null)).isTrue();
    }

    /**
     * Un jeton absent signale un formulaire poste hors du navigateur, pas une panne :
     * il est refuse <b>sans appel</b>. Aucune attente n est posee sur le serveur, donc
     * tout appel ferait echouer ce test.
     */
    @Test
    @DisplayName("jeton absent : refusé sans aucun appel")
    void absentSansAppel() {
        avecServeur();

        assertThat(verificateur.jetonValide(null, null)).isFalse();
        assertThat(verificateur.jetonValide("  ", null)).isFalse();

        serveur.verify();
    }
}
