package be.autoservplus.intervention.web.dto;

import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.intervention.domain.PieceIncluse;

import java.util.Set;
import java.util.UUID;

/**
 * Pièce incluse d'une ligne de forfait, libellé dans la langue de l'écran (V77). Le membre la
 * lit sans prix ni stock : elle est comprise dans le forfait.
 *
 * @param aCommander le stock ne couvre pas les travaux à venir (Q-H-30), au back-office seulement
 */
public record PieceIncluseVue(UUID referencePiece, String libelle, int quantite, boolean aCommander) {

    public static PieceIncluseVue de(PieceIncluse incluse, Set<Long> piecesACommander) {
        return new PieceIncluseVue(
                incluse.getPiece().getReference(),
                incluse.getPiece().libelle(LanguesSupportees.deLEcran()).texte(),
                incluse.getQuantite(),
                piecesACommander.contains(incluse.getPiece().getId()));
    }
}
