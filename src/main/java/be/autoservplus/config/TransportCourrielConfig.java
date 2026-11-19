package be.autoservplus.config;

import be.autoservplus.communication.service.TransportConsole;
import be.autoservplus.communication.service.TransportCourriel;
import be.autoservplus.communication.service.TransportSmtp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Choix du transport des courriels : le relais SMTP quand {@code SMTP_HOTE} est
 * renseigné, le journal sinon. Une seule méthode décide, plutôt que deux beans
 * conditionnels dont l'ordre d'évaluation n'est pas défini.
 *
 * <p>Le nom annoncé au relais ({@code EHLO}) et le domaine du {@code Message-ID} sont
 * l'hôte de {@code autoservplus.url-publique}. Sans eux, Jakarta Mail les demande à
 * {@code InetAddress.getCanonicalHostName()}, une recherche de nom inverse que les délais
 * SMTP ne bornent pas : sur un poste dont une carte réseau porte une adresse sans nom
 * (169.254.x.x), elle coûtait environ dix secondes chacune, vingt par courriel, dans la
 * requête qui attend la remise.</p>
 */
@Configuration
public class TransportCourrielConfig {

    @Bean
    TransportCourriel transportCourriel(ProprietesCourriel proprietes, IdentiteGarage garage,
                                        @Value("${autoservplus.url-publique:}") String urlPublique) {
        if (!proprietes.relaisConfigure()) {
            return new TransportConsole();
        }
        return new TransportSmtp(emetteur(proprietes, hoteAnnonce(urlPublique)),
                proprietes.expediteur(), garage.enseigne());
    }

    /**
     * Hôte de l'URL publique, sans schéma ni port. Une URL absente ou sans hôte refuse le
     * démarrage : se rabattre sur le nom de la machine rendrait la recherche inverse que
     * ce réglage écarte.
     */
    static String hoteAnnonce(String urlPublique) {
        String hote = null;
        if (urlPublique != null && !urlPublique.isBlank()) {
            try {
                hote = new URI(urlPublique.strip()).getHost();
            } catch (URISyntaxException invalide) {
                hote = null;
            }
        }
        if (hote == null || hote.isBlank()) {
            throw new IllegalStateException("URL_PUBLIQUE (autoservplus.url-publique) doit être "
                    + "une URL absolue avec un hôte, par exemple https://garage.exemple.be : "
                    + "son hôte est le nom annoncé au relais SMTP. Valeur reçue : « "
                    + urlPublique + " ».");
        }
        return hote;
    }

    static JavaMailSenderImpl emetteur(ProprietesCourriel proprietes, String hoteAnnonce) {
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
        reglages.put("mail.smtp.localhost", hoteAnnonce);
        reglages.put("mail.host", hoteAnnonce);
        return emetteur;
    }
}
