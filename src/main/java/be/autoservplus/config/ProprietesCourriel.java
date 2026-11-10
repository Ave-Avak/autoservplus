package be.autoservplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Expéditeur et relais SMTP des courriels transactionnels ({@code autoservplus.courriel}).
 *
 * <p>Propriétés propres au projet plutôt que {@code spring.mail.*} : la configuration
 * automatique de Spring Boot crée un émetteur dès que {@code spring.mail.host} existe,
 * même vide. Une valeur par défaut vide dans {@code application.yml} aurait donc produit
 * un émetteur sans hôte, que chaque envoi aurait fait échouer.</p>
 *
 * @param expediteur adresse d'expédition, sur un domaine authentifié en production
 * @param smtp       relais, absent tant que {@code SMTP_HOTE} est vide
 */
@ConfigurationProperties(prefix = "autoservplus.courriel")
public record ProprietesCourriel(String expediteur, Smtp smtp) {

    /**
     * Relais SMTP. {@code starttls} chiffre la session avant l'authentification, exigé par
     * Brevo sur le port 587 et sans objet pour Mailpit. Les deux délais bornent l'attente
     * de la requête qui a demandé le courriel.
     */
    public record Smtp(String hote, Integer port, String identifiant, String secret,
                       boolean starttls, Duration delaiConnexion, Duration delaiLecture) {
    }

    public boolean relaisConfigure() {
        return smtp != null && renseigne(smtp.hote());
    }

    /** Le relais demande une authentification quand un identifiant est fourni. */
    public boolean authentifie() {
        return smtp != null && renseigne(smtp.identifiant());
    }

    /**
     * Refuse le démarrage sous {@code prod} sans relais ni expéditeur, toutes les
     * variables fautives nommées en une fois. Le repli sur le journal écrit les liens à
     * jeton : il n'a pas sa place en production.
     */
    public void verifierPourProduction() {
        List<String> fautes = new ArrayList<>();
        if (!relaisConfigure()) {
            fautes.add("SMTP_HOTE absente ou vide");
        }
        if (smtp == null || smtp.port() == null) {
            fautes.add("SMTP_PORT absente ou vide");
        }
        if (!renseigne(expediteur)) {
            fautes.add("COURRIEL_EXPEDITEUR absente ou vide");
        }
        if (authentifie() && !renseigne(smtp.secret())) {
            fautes.add("SMTP_SECRET absente ou vide alors que SMTP_IDENTIFIANT est fourni");
        }
        if (!fautes.isEmpty()) {
            throw new IllegalStateException(
                    "Relais de courriel absent, démarrage interrompu. Variables à corriger :\n  - "
                            + String.join("\n  - ", fautes));
        }
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
