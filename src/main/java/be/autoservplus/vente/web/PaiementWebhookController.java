package be.autoservplus.vente.web;

import be.autoservplus.vente.service.PaiementService;
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
 * <p>Premier {@code @RestController} du projet : un webhook rend un statut HTTP,
 * pas une vue Thymeleaf.</p>
 */
@RestController
@RequestMapping("/webhooks")
public class PaiementWebhookController {

    private final PaiementService service;

    public PaiementWebhookController(PaiementService service) {
        this.service = service;
    }

    @PostMapping("/paiement")
    public ResponseEntity<Void> notificationPaiement(@RequestParam("id") String id) {
        service.traiterNotification(id);
        return ResponseEntity.ok().build();
    }
}
