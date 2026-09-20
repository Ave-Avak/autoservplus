package be.autoservplus.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client HTTP de la verification des mots de passe compromis.
 *
 * <p>Un {@code RestClient} deja arme plutot qu un {@code Builder}, comme pour Mollie :
 * l adresse et les delais sont decides ici, une fois, et le service appelant ne peut
 * pas les contredire.</p>
 *
 * <p><b>Deux secondes</b>, et non les cinq de la passerelle de paiement : le visiteur
 * attend devant un formulaire, et cette verification est un supplement — elle doit
 * renoncer vite plutot que de faire patienter.</p>
 *
 * <p>Aucune cle d API : l interface de plage de Have I Been Pwned est ouverte. Rien a
 * configurer, rien a proteger, rien a faire fuiter.</p>
 */
@Configuration
@ConditionalOnProperty(name = "autoservplus.securite.compromission.activee",
        havingValue = "true", matchIfMissing = true)
public class CompromissionClientConfig {

    private static final String URL_API = "https://api.pwnedpasswords.com";

    /** Delai au-dela duquel la verification est abandonnee sans refuser le mot de passe. */
    public static final Duration DELAI_MAXIMUM = Duration.ofSeconds(2);

    @Bean
    public RestClient clientCompromission() {
        return RestClient.builder()
                .baseUrl(URL_API)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(DELAI_MAXIMUM)
                                .withReadTimeout(DELAI_MAXIMUM)))
                // Demande au service de ne pas completer sa reponse par du bruit : le
                // k-anonymat protege deja, cet en-tete evite d avoir a trier.
                .defaultHeader("Add-Padding", "false")
                .defaultHeader(HttpHeaders.USER_AGENT, "AutoServPlus")
                .build();
    }
}
