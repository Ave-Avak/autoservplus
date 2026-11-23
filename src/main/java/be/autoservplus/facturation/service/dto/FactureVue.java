package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.domain.Facture;

import java.util.UUID;

/**
 * Facture telle que la voit un écran : de quoi construire un lien de
 * téléchargement, et rien de plus. L'entité ne franchit pas la couche service.
 *
 * <p>{@code referenceCommande} permet de rattacher la facture à sa ligne dans
 * l'historique des commandes sans que le module vente ait à connaître la
 * facturation.</p>
 */
public record FactureVue(UUID reference, String numero, UUID referenceCommande) {

    public static FactureVue de(Facture facture) {
        return new FactureVue(facture.getReference(), facture.getNumero(),
                facture.getCommande() == null ? null : facture.getCommande().getReference());
    }
}
