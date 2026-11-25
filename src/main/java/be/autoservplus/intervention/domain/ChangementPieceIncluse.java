package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Piece;

import java.util.Objects;

/**
 * Un changement des pièces incluses d'une ligne, tel que le domaine l'a appliqué, pour que le
 * service l'écrive au journal sans le reconstituer.
 *
 * @param ligne    ligne concernée, {@code null} quand elle est retirée avec ses pièces : la
 *                 trace ne pointe plus vers elle ({@code ON DELETE SET NULL})
 * @param avant    quantité avant, {@code null} pour une copie ou un ajout
 * @param apres    quantité après, {@code null} pour un retrait
 */
public record ChangementPieceIncluse(LigneIntervention ligne, Piece piece, EvenementPieceIncluse evenement,
                                     Integer avant, Integer apres) {

    public ChangementPieceIncluse {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(evenement, "evenement");
    }
}
