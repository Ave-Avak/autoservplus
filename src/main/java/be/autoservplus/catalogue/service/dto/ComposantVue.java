package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.ComposantPrestation;

import java.util.UUID;

/**
 * Pièce de la composition d'une prestation, au formulaire du back-office.
 *
 * @param pieceActive {@code false} pour une pièce désactivée, que le formulaire signale
 */
public record ComposantVue(UUID pieceReference, String referenceFabricant, String libelle,
                           int quantite, boolean pieceActive) {

    public static ComposantVue de(ComposantPrestation composant) {
        return new ComposantVue(composant.getPiece().getReference(),
                composant.getPiece().getReferenceFabricant(), composant.getPiece().getLibelle(),
                composant.getQuantite(), composant.getPiece().isActif());
    }
}
