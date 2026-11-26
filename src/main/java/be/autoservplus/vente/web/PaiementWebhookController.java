package be.autoservplus.vente.web;

import be.autoservplus.common.debit.Plafonne;

import be.autoservplus.vente.service.IssueRelecture;
import be.autoservplus.vente.service.PaiementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Notifications entrantes du prestataire de paiement (convention Mollie : un
 * POST formulaire portant le seul champ {@code id}).
 *
 * <p>L'endpoint est public et hors CSRF (le prestataire n'a ni session ni jeton).
 * C'est assumé par la stratégie sécurité §11 : le payload n'est JAMAIS cru,
 * l'identifiant sert uniquement à retrouver le paiement et le statut authentique
 * est relu auprès du prestataire par le service. Réponse 200 vide. Référence
 * inconnue : 404. Erreur interne : 500 et le prestataire rejouera. Le
 * traitement est idempotent, un rejeu est sans double effet.</p>
 *
 * <p><b>Ce contrôleur n'attrape PAS la panne du prestataire, et c'est voulu.</b>
 * Partout ailleurs, une {@code PrestataireIndisponibleException} est traduite en
 * message lisible pour ne jamais rompre un parcours humain. Ici, le correspondant
 * est une machine : un 200 rendu alors que le statut n'a pas pu être relu signifie
 * « c'est traité » et le prestataire ne rappellera jamais. La commande resterait
 * en attente après un encaissement réel. Le 500 est au contraire le seul moyen de
 * demander un rejeu, et le traitement étant idempotent, ce rejeu est sans risque.
 * Un test verrouille cette asymétrie, qui se lit sinon comme un oubli.</p>
 *
 * <p>Premier {@code @RestController} du projet : un webhook rend un statut HTTP,
 * pas une vue Thymeleaf.</p>
 *
 * <p><b>La réception est tracée</b>, en pendant de la ligne écrite au retour du
 * membre. C'est le seul moyen de constater ce que docs/deploiement.md promet de
 * montrer : les deux déclencheurs arrivent tour à tour et la facture n'est émise
 * qu'une fois. Sans ces deux lignes, l'idempotence reste une affirmation : le
 * journal ne distinguait même pas lequel des deux chemins venait de s'exécuter.</p>
 */
@RestController
@RequestMapping("/webhooks")
public class PaiementWebhookController {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(PaiementWebhookController.class);

    private final PaiementService service;

    public PaiementWebhookController(PaiementService service) {
        this.service = service;
    }

    /**
     * Forme d'un identifiant de paiement Mollie ({@code tr_} suivi de lettres et de chiffres),
     * soulignés admis pour le prestataire fictif. Vérifiée avant toute lecture en base, et
     * seule une valeur conforme est recopiée au journal : un retour à la ligne glissé dans
     * le champ y écrirait sinon une fausse ligne.
     */
    static final Pattern FORMAT_IDENTIFIANT = Pattern.compile("tr_[A-Za-z0-9_]{1,64}");

    @Plafonne("webhook-paiement")
    @PostMapping("/paiement")
    public ResponseEntity<Void> notificationPaiement(@RequestParam("id") String id) {
        if (!FORMAT_IDENTIFIANT.matcher(id).matches()) {
            // Même réponse qu'une référence inconnue : la forme attendue ne se devine pas.
            JOURNAL.info("Notification du prestataire refusée, identifiant hors format.");
            return ResponseEntity.notFound().build();
        }
        IssueRelecture issue = service.traiterNotification(id);
        // L'identifiant du paiement chez le prestataire, et rien d'autre : ni montant,
        // ni membre. C'est la seule donnée par laquelle cette ligne se rapproche de
        // celle du retour du membre, et elle n'identifie personne.
        // La ligne n'est atteinte que si la relecture a abouti : une panne du
        // prestataire remonte en 500 pour provoquer un rejeu, et journaliser un
        // « statut relu » que l'on n'a pas pu relire serait faux.
        JOURNAL.info("Notification du prestataire pour le paiement {} : statut relu = {}, {}.",
                id, issue.statutRelu(), issue.effet().libelle());
        return ResponseEntity.ok().build();
    }
}
