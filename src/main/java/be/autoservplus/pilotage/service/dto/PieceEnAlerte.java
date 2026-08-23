package be.autoservplus.pilotage.service.dto;

import be.autoservplus.catalogue.domain.Piece;

import java.util.UUID;

/**
 * Une piece dont le stock a atteint son seuil d alerte (BL-1).
 *
 * <p>DTO propre au tableau de bord plutot que l entite {@code Piece} : le repository
 * du catalogue rend des entites, et les laisser remonter jusqu au gabarit exposerait
 * prix d achat et description a un ecran qui n en a pas besoin.</p>
 */
public record PieceEnAlerte(UUID reference, String libelle, int stock, int seuil) {

    public static PieceEnAlerte de(Piece piece) {
        return new PieceEnAlerte(piece.getReference(), piece.getLibelle(),
                piece.getQuantiteStock(), piece.getSeuilAlerte());
    }

    /** Rupture averee, distincte du simple franchissement de seuil. */
    public boolean enRupture() {
        return stock <= 0;
    }
}
