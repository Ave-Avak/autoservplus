package be.autoservplus.config;

import be.autoservplus.communication.service.TransportConsole;
import be.autoservplus.communication.service.TransportCourriel;
import be.autoservplus.communication.service.TransportSmtp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Choix du transport des courriels : le relais SMTP quand {@code SMTP_HOTE} est
 * renseigné, le journal sinon. Une seule méthode décide, plutôt que deux beans
 * conditionnels dont l'ordre d'évaluation n'est pas défini.
 */
@Configuration
public class TransportCourrielConfig {

    @Bean
    TransportCourriel transportCourriel(ProprietesCourriel proprietes, IdentiteGarage garage) {
        if (!proprietes.relaisConfigure()) {
            return new TransportConsole();
        }
        return new TransportSmtp(emetteur(proprietes), proprietes.expediteur(), garage.enseigne());
    }

    static JavaMailSenderImpl emetteur(ProprietesCourriel proprietes) {
        ProprietesCourriel.Smtp smtp = proprietes.smtp();
        JavaMailSenderImpl emetteur = new JavaMailSenderImpl();
        emetteur.setHost(smtp.hote());
        emetteur.setPort(smtp.port());
        emetteur.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (proprietes.authentifie()) {
            emetteur.setUsername(smtp.identifiant());
            emetteur.setPassword(smtp.secret());
        }
        Properties reglages = emetteur.getJavaMailProperties();
        reglages.put("mail.transport.protocol", "smtp");
        reglages.put("mail.smtp.auth", String.valueOf(proprietes.authentifie()));
        reglages.put("mail.smtp.starttls.enable", String.valueOf(smtp.starttls()));
        // Exigé et pas seulement permis : un relais qui refuserait STARTTLS ne doit pas
        // recevoir l'identifiant en clair.
        reglages.put("mail.smtp.starttls.required", String.valueOf(smtp.starttls()));
        reglages.put("mail.smtp.connectiontimeout", String.valueOf(smtp.delaiConnexion().toMillis()));
        reglages.put("mail.smtp.timeout", String.valueOf(smtp.delaiLecture().toMillis()));
        reglages.put("mail.smtp.writetimeout", String.valueOf(smtp.delaiLecture().toMillis()));
        return emetteur;
    }
}
