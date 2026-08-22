package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.domain.Facture;

import java.util.UUID;

/**
 * Facture telle que la voit un ecran : de quoi construire un lien de
 * telechargement, et rien de plus. L entite ne franchit pas la couche service.
 *
 * <p>{@code referenceCommande} permet de rattacher la facture a sa ligne dans
 * l historique des commandes sans que le module vente ait a connaitre la
 * facturation.</p>
 */
public record FactureVue(UUID reference, String numero, UUID referenceCommande) {

    public static FactureVue de(Facture facture) {
        return new FactureVue(facture.getReference(), facture.getNumero(),
                facture.getCommande() == null ? null : facture.getCommande().getReference());
    }
}
