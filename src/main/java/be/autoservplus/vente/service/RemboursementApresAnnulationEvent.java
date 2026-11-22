package be.autoservplus.vente.service;

import java.util.UUID;

/**
 * Un paiement reçu sur une commande annulée vient d'être remboursé par un
 * administrateur. Le membre en est prévenu après la validation de la transaction.
 *
 * @param referencePaiement référence interne du paiement
 */
public record RemboursementApresAnnulationEvent(UUID referencePaiement) {
}
