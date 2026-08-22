package be.autoservplus.catalogue.service.dto;

import java.math.BigDecimal;

/**
 * Donnees saisies par l administrateur pour creer (A4) ou modifier (A5) une piece.
 *
 * <p>{@code referenceFabricant} n est lue qu a la creation : c est l ancre d unicite
 * de la piece ({@code uq_piece_fabricant}), immuable ensuite comme le code d une
 * prestation. {@code quantiteStock} est le stock initial a la creation ; en
 * modification, il corrige l inventaire courant.</p>
 */
public record DonneesPiece(
        String codeCategorie,
        String referenceFabricant,
        String libelle,
        String marque,
        String description,
        BigDecimal prixHtva,
        BigDecimal tauxTva,
        int quantiteStock,
        int seuilAlerte,
        boolean actif) {
}
