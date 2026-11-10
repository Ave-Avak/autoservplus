package be.autoservplus.config;

import be.autoservplus.communication.service.TransportConsole;
import be.autoservplus.communication.service.TransportCourriel;
import be.autoservplus.communication.service.TransportSmtp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Relais SMTP exigé sous {@code prod}, facultatif ailleurs, lu dans le vrai
 * {@code application.yml}, comme {@link ControleIdentiteGarageTest}.
 */
@DisplayName("Relais de courriel au démarrage")
class ControleCourrielProductionTest {

    private static final String[] RELAIS = {
            "autoservplus.courriel.smtp.hote=smtp-relay.exemple.test",
            "autoservplus.courriel.smtp.port=587",
            "autoservplus.courriel.smtp.identifiant=compte",
            "autoservplus.courriel.smtp.secret=secret",
            "autoservplus.courriel.expediteur=ne-pas-repondre@exemple.test"};

    private static ApplicationContextRunner sous(String profil) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yml",
                        "spring.profiles.active=" + profil)
                .withUserConfiguration(ProprietesConfig.class, ControleCourrielProduction.class,
                        TransportCourrielConfig.class);
    }

    @Nested
    @DisplayName("Sous prod")
    class Production {

        @Test
        @DisplayName("refusé sans relais ni expéditeur, toutes les variables nommées")
        void sansRelais() {
            sous("prod").run(contexte -> {
                assertThat(contexte).hasFailed();
                assertThat(contexte.getStartupFailure()).rootCause()
                        .hasMessageContaining("SMTP_HOTE absente ou vide")
                        .hasMessageContaining("COURRIEL_EXPEDITEUR absente ou vide");
            });
        }

        @Test
        @DisplayName("refusé avec un identifiant sans secret")
        void identifiantSansSecret() {
            sous("prod").withPropertyValues(RELAIS)
                    .withPropertyValues("autoservplus.courriel.smtp.secret=")
                    .run(contexte -> {
                        assertThat(contexte).hasFailed();
                        assertThat(contexte.getStartupFailure()).rootCause()
                                .hasMessageContaining("SMTP_SECRET")
                                .hasMessageNotContaining("SMTP_HOTE");
                    });
        }

        @Test
        @DisplayName("accepté avec un relais complet, qui devient le transport")
        void relaisComplet() {
            sous("prod").withPropertyValues(RELAIS).run(contexte -> {
                assertThat(contexte).hasNotFailed();
                assertThat(contexte.getBean(TransportCourriel.class)).isInstanceOf(TransportSmtp.class);
            });
        }
    }

    @Test
    @DisplayName("sous dev, sans relais, le journal prend le relais")
    void devSansRelais() {
        sous("dev").run(contexte -> {
            assertThat(contexte).hasNotFailed();
            assertThat(contexte.getBean(TransportCourriel.class)).isInstanceOf(TransportConsole.class);
        });
    }

    @Test
    @DisplayName("l'émetteur porte des délais courts, STARTTLS exigé et l'authentification")
    void reglagesDeLEmetteur() {
        ProprietesCourriel proprietes = new ProprietesCourriel("ne-pas-repondre@exemple.test",
                new ProprietesCourriel.Smtp("smtp-relay.exemple.test", 587, "compte", "secret",
                        true, Duration.ofSeconds(5), Duration.ofSeconds(10)));

        JavaMailSenderImpl emetteur = TransportCourrielConfig.emetteur(proprietes);
        Properties reglages = emetteur.getJavaMailProperties();

        assertThat(emetteur.getHost()).isEqualTo("smtp-relay.exemple.test");
        assertThat(emetteur.getUsername()).isEqualTo("compte");
        assertThat(reglages.getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(reglages.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(reglages.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
        assertThat(reglages.getProperty("mail.smtp.timeout")).isEqualTo("10000");
    }

    @Test
    @DisplayName("sans identifiant, aucune authentification n'est tentée")
    void relaisSansAuthentification() {
        ProprietesCourriel proprietes = new ProprietesCourriel("ne-pas-repondre@exemple.test",
                new ProprietesCourriel.Smtp("localhost", 1025, "", "", false,
                        Duration.ofSeconds(5), Duration.ofSeconds(10)));

        JavaMailSenderImpl emetteur = TransportCourrielConfig.emetteur(proprietes);

        assertThat(emetteur.getUsername()).isNull();
        assertThat(emetteur.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
    }
}
