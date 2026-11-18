package be.autoservplus.communication.service;

import java.util.List;
import java.util.Objects;

/**
 * Courriel rédigé, prêt à partir : texte brut, dans la langue du destinataire.
 *
 * @param type          nom court du message (« verification-adresse »...), seul élément
 *                      repris par le journal en cas d'échec, avec l'erreur
 * @param piecesJointes dans l'ordre où le destinataire les lira, éventuellement aucune.
 *                      Une liste et non une pièce unique : la confirmation d'une commande
 *                      joint les CGV acceptées et le formulaire de rétractation.
 */
public record Courriel(String type,
                       String adresse,
                       String nomDestinataire,
                       String sujet,
                       String corps,
                       List<PieceJointeCourriel> piecesJointes) {

    public Courriel {
        piecesJointes = piecesJointes == null ? List.of() : List.copyOf(piecesJointes);
        piecesJointes.forEach(piece -> Objects.requireNonNull(piece, "piece jointe"));
    }
}
