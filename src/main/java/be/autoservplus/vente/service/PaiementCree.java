package be.autoservplus.vente.service;

/**
 * Resultat de la creation d un paiement chez le prestataire.
 *
 * @param referencePrestataire identifiant du paiement chez le prestataire
 *                             (celui que renverra le webhook)
 * @param urlRedirection       page de paiement vers laquelle envoyer le membre
 */
public record PaiementCree(String referencePrestataire, String urlRedirection) {
}
