package be.autoservplus.vente.web;

import be.autoservplus.vente.service.IssueRelecture;
import be.autoservplus.vente.service.PaiementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notifications entrantes du prestataire de paiement (convention Mollie : un
 * POST formulaire portant le seul champ {@code id}).
 *
 * <p>L endpoint est public et hors CSRF (le prestataire n a ni session ni jeton)
 * — c est assume par la strategie securite §11 : le payload n est JAMAIS cru,
 * l identifiant sert uniquement a retrouver le paiement et le statut authentique
 * est relu aupres du prestataire par le service. Reponse 200 vide ; reference
 * inconnue : 404 ; erreur interne : 500 et le prestataire rejouera — le
 * traitement est idempotent, un rejeu est sans double effet.</p>
 *
 * <p><b>Ce controleur n attrape PAS la panne du prestataire, et c est voulu.</b>
 * Partout ailleurs, une {@code PrestataireIndisponibleException} est traduite en
 * message lisible pour ne jamais rompre un parcours humain. Ici, le correspondant
 * est une machine : un 200 rendu alors que le statut n a pas pu etre relu signifie
 * « c est traite » et le prestataire ne rappellera jamais — la commande resterait
 * en attente apres un encaissement reel. Le 500 est au contraire le seul moyen de
 * demander un rejeu, et le traitement etant idempotent, ce rejeu est sans risque.
 * Un test verrouille cette asymetrie, qui se lit sinon comme un oubli.</p>
 *
 * <p>Premier {@code @RestController} du projet : un webhook rend un statut HTTP,
 * pas une vue Thymeleaf.</p>
 *
 * <p><b>La reception est tracee</b>, en pendant de la ligne ecrite au retour du
 * membre. C est le seul moyen de constater ce que docs/deploiement.md promet de
 * montrer : les deux declencheurs arrivent tour a tour et la facture n est emise
 * qu une fois. Sans ces deux lignes, l idempotence reste une affirmation — le
 * journal ne distinguait meme pas lequel des deux chemins venait de s executer.</p>
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

    @PostMapping("/paiement")
    public ResponseEntity<Void> notificationPaiement(@RequestParam("id") String id) {
        IssueRelecture issue = service.traiterNotification(id);
        // L identifiant du paiement chez le prestataire, et rien d autre : ni montant,
        // ni membre. C est la seule donnee par laquelle cette ligne se rapproche de
        // celle du retour du membre, et elle n identifie personne.
        // La ligne n est atteinte que si la relecture a abouti : une panne du
        // prestataire remonte en 500 pour provoquer un rejeu, et journaliser un
        // « statut relu » que l on n a pas pu relire serait faux.
        JOURNAL.info("Notification du prestataire pour le paiement {} : statut relu = {}, {}.",
                id, issue.statutRelu(), issue.effet().libelle());
        return ResponseEntity.ok().build();
    }
}
