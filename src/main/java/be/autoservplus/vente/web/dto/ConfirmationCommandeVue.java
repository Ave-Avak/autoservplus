package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.StatutCommande;

import java.util.UUID;

/**
 * Vue de la page « commande enregistree » (F14). Le numero est la piece
 * d identite que le membre conserve ; le montant TVAC rappelle ce qui est du au
 * paiement. Montant pre-formate en euros, convention du module.
 *
 * <p>{@code enAttenteDePaiement} existe parce que cette page est desormais le point
 * de chute du RETOUR de paiement : y proposer « Proceder au paiement » a un membre
 * qui vient de payer serait au mieux deroutant, au pire une seconde tentative. Un
 * booleen plutot que le statut brut — l ecran n a qu une decision a prendre, et lui
 * passer l enumeration l inviterait a en prendre d autres.</p>
 */
public record ConfirmationCommandeVue(
        UUID reference,
        String numero,
        String totalTvac,
        boolean enAttenteDePaiement) {

    public static ConfirmationCommandeVue de(Commande commande) {
        return new ConfirmationCommandeVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.euros(commande.getMontantTvac()),
                commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT);
    }
}
