package be.autoservplus.vente.service;

import java.util.UUID;

/**
 * Un paiement vient d'être encaissé sur une commande déjà annulée. Publié une fois, à
 * l'enregistrement de l'encaissement : le garage et le membre en sont prévenus après la
 * validation de la transaction, comme pour {@link CommandePayeeEvent}.
 *
 * @param referencePaiement référence interne du paiement, jamais celle du prestataire
 * @param referenceCommande commande annulée que ce paiement visait
 */
public record PaiementApresAnnulationEvent(UUID referencePaiement, UUID referenceCommande) {
}
