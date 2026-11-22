package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Traductions;

import java.math.BigDecimal;

/**
 * Donnees saisies par l administrateur pour creer (A4) ou modifier (A5) une piece.
 *
 * <p>{@code referenceFabricant} n est lue qu a la creation : c est l ancre d unicite
 * de la piece ({@code uq_piece_fabricant}), immuable ensuite comme le code d une
 * prestation. {@code quantiteStock} est le stock initial a la creation. En
 * modification, il corrige l inventaire courant.</p>
 *
 * @param traductions libellé et description en néerlandais et en anglais, ou {@code null}
 *                    pour laisser les traductions existantes en l'état
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
        boolean actif,
        Traductions traductions) {

    /** Sans traductions : celles de la pièce restent ce qu'elles sont, voir {@link DonneesPrestation}. */
    public DonneesPiece(String codeCategorie, String referenceFabricant, String libelle, String marque,
                        String description, BigDecimal prixHtva, BigDecimal tauxTva, int quantiteStock,
                        int seuilAlerte, boolean actif) {
        this(codeCategorie, referenceFabricant, libelle, marque, description, prixHtva, tauxTva, quantiteStock,
                seuilAlerte, actif, null);
    }
}
