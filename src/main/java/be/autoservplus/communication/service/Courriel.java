package be.autoservplus.communication.service;

/**
 * Courriel rédigé, prêt à partir : texte brut, dans la langue du destinataire.
 *
 * @param type        nom court du message (« verification-adresse »...), seul élément
 *                    repris par le journal en cas d'échec, avec l'erreur
 * @param pieceJointe facultative
 */
public record Courriel(String type,
                       String adresse,
                       String nomDestinataire,
                       String sujet,
                       String corps,
                       PieceJointeCourriel pieceJointe) {
}
