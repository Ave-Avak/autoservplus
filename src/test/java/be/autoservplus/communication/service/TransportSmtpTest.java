package be.autoservplus.communication.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Un relais injoignable : rien ne remonte, et le journal ne porte ni lien ni jeton. */
@DisplayName("Remise SMTP en échec")
class TransportSmtpTest {

    private ListAppender<ILoggingEvent> journal;

    @BeforeEach
    void ecouter() {
        journal = new ListAppender<>();
        journal.start();
        ((Logger) LoggerFactory.getLogger(TransportSmtp.class)).addAppender(journal);
    }

    @AfterEach
    void detacher() {
        ((Logger) LoggerFactory.getLogger(TransportSmtp.class)).detachAppender(journal);
    }

    @Test
    @DisplayName("relais injoignable : aucune exception, attente bornée, journal sans corps")
    void relaisInjoignable() {
        JavaMailSenderImpl mort = new JavaMailSenderImpl();
        mort.setHost("localhost");
        mort.setPort(1);
        mort.getJavaMailProperties().put("mail.smtp.connectiontimeout", "1000");
        TransportSmtp transport = new TransportSmtp(mort, "ne-pas-repondre@autoservplus.test",
                "Garage Test");
        Courriel courriel = new Courriel("reinitialisation", "marie@exemple.be", "Marie",
                "Sujet", "Suivez http://localhost:8080/mot-de-passe/nouveau?jeton=abc123", null);
        Instant avant = Instant.now();

        assertThatCode(() -> transport.transmettre(courriel)).doesNotThrowAnyException();

        assertThat(Duration.between(avant, Instant.now())).isLessThan(Duration.ofSeconds(5));
        assertThat(journal.list).hasSize(1);
        assertThat(journal.list.get(0).getFormattedMessage())
                .contains("reinitialisation")
                .doesNotContain("jeton")
                .doesNotContain("http");
    }
}
