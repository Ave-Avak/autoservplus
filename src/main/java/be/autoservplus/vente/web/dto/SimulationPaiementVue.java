package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Paiement;

import java.util.UUID;

/**
 * Vue de la page de paiement SIMULE, qui tient le rôle de la page du prestataire
 * quand aucun prestataire réel n'est configuré.
 *
 * <p>Elle porte le numéro et le montant pour une raison précise : c'est ce que le
 * membre doit pouvoir confronter à son récapitulatif avant de payer, chez Mollie
 * comme ici. Une page de simulation qui n'afficherait que des boutons ne
 * simulerait pas la seule chose qui compte à cet instant, vérifier qu'on paie le
 * bon montant pour la bonne commande.</p>
 *
 * <p>{@code referenceCommande} n'est pas affichée : elle sert à ramener le membre
 * sur sa commande une fois l'issue simulée.</p>
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
                FormatageRdv.eurosEcran(paiement.getMontant()));
    }
}
