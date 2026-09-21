package be.autoservplus.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client HTTP de la verification Turnstile.
 *
 * <p>Meme forme que pour Mollie et la verification de compromission : un
 * {@code RestClient} deja arme, adresse et delais decides une fois.</p>
 *
 * <p>La configuration entiere depend de {@code cle-secrete}. Sans elle, ni ce client
 * ni {@code VerificateurTurnstile} n existent, et le widget n est pas rendu : le site
 * fonctionne sur ses trois autres couches. Une cle absente est un deploiement sans
 * Turnstile, pas un deploiement casse.</p>
 */
@Configuration
@SiTurnstileConfigure
public class TurnstileClientConfig {

    private static final String URL_VERIFICATION =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    /**
     * Trois secondes. Plus court que les cinq du paiement — le visiteur attend devant
     * un formulaire — mais plus long que les deux de la verification de compromission,
     * celle-ci etant un supplement quand Turnstile est une etape du parcours.
     */
    public static final Duration DELAI_MAXIMUM = Duration.ofSeconds(3);

    @Bean
    public RestClient clientTurnstile() {
        return RestClient.builder()
                .baseUrl(URL_VERIFICATION)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(DELAI_MAXIMUM)
                                .withReadTimeout(DELAI_MAXIMUM)))
                .build();
    }
}
