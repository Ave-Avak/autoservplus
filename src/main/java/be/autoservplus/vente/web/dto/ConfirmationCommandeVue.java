package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;

import java.util.UUID;

/**
 * Vue de la page « commande enregistree » (F14). Le numero est la piece
 * d identite que le membre conserve ; le montant TVAC rappelle ce qui sera du au
 * paiement (etape suivante, hors de ce bloc). Montant pre-formate en euros,
 * convention du module.
 */
public record ConfirmationCommandeVue(
        UUID reference,
        String numero,
        String totalTvac) {

    public static ConfirmationCommandeVue de(Commande commande) {
        return new ConfirmationCommandeVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.euros(commande.getMontantTvac()));
    }
}
