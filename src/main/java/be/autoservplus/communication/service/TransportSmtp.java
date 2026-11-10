package be.autoservplus.communication.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Remise à un relais SMTP, quel que soit le fournisseur : Brevo en production,
 * Mailpit sur le poste, Mailtrap pour un serveur de démonstration. Aucun appel propre
 * à un fournisseur, le relais est un réglage.
 *
 * <p>Texte brut seulement : pas de HTML, donc ni pixel de suivi ni lien réécrit.</p>
 */
public class TransportSmtp implements TransportCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(TransportSmtp.class);

    private final JavaMailSender emetteur;
    private final String expediteur;
    private final String nomExpediteur;

    /**
     * @param nomExpediteur nom affiché devant l'adresse, celui du garage : le membre
     *                      reconnaît son garage, pas une adresse technique
     */
    public TransportSmtp(JavaMailSender emetteur, String expediteur, String nomExpediteur) {
        this.emetteur = emetteur;
        this.expediteur = expediteur;
        this.nomExpediteur = nomExpediteur;
    }

    @Override
    public void transmettre(Courriel courriel) {
        try {
            MimeMessage message = emetteur.createMimeMessage();
            boolean avecPiece = courriel.pieceJointe() != null;
            MimeMessageHelper redaction = new MimeMessageHelper(
                    message, avecPiece, StandardCharsets.UTF_8.name());
            redaction.setFrom(expediteur, nomExpediteur);
            redaction.setTo(courriel.adresse());
            redaction.setSubject(courriel.sujet());
            redaction.setText(courriel.corps(), false);
            if (avecPiece) {
                PieceJointeCourriel piece = courriel.pieceJointe();
                redaction.addAttachment(piece.nomFichier(),
                        new ByteArrayResource(piece.contenu()), piece.typeMime());
            }
            emetteur.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException echec) {
            // Le type et la cause seulement : ni corps, qui porte parfois un lien à
            // jeton, ni adresse du destinataire.
            Throwable cause = racine(echec);
            JOURNAL.warn("Courriel {} non remis au relais SMTP : {} ({})", courriel.type(),
                    cause.getClass().getSimpleName(), cause.getMessage());
        }
    }

    private static Throwable racine(Throwable erreur) {
        Throwable courante = erreur;
        while (courante.getCause() != null && courante.getCause() != courante) {
            courante = courante.getCause();
        }
        return courante;
    }
}
