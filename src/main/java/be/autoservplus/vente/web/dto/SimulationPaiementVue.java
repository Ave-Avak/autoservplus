package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Paiement;

import java.util.UUID;

/**
 * Vue de la page de paiement SIMULE, qui tient le role de la page du prestataire
 * quand aucun prestataire reel n est configure.
 *
 * <p>Elle porte le numero et le montant pour une raison precise : c est ce que le
 * membre doit pouvoir confronter a son recapitulatif avant de payer, chez Mollie
 * comme ici. Une page de simulation qui n afficherait que des boutons ne
 * simulerait pas la seule chose qui compte a cet instant — verifier qu on paie le
 * bon montant pour la bonne commande.</p>
 *
 * <p>{@code referenceCommande} n est pas affichee : elle sert a ramener le membre
 * sur sa commande une fois l issue simulee.</p>
 */
public record SimulationPaiementVue(
        String referencePrestataire,
        UUID referenceCommande,
        String numeroCommande,
        String totalTvac) {

    public static SimulationPaiementVue de(Paiement paiement) {
        return new SimulationPaiementVue(
                paiement.getReferenceMollie(),
                paiement.getCommande().getReference(),
                paiement.getCommande().getNumero(),
                FormatageRdv.euros(paiement.getMontant()));
    }
}
