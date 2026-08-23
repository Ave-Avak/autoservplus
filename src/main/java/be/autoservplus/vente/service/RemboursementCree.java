package be.autoservplus.vente.service;

/**
 * Resultat de la creation d un remboursement chez le prestataire.
 *
 * <p>Un seul champ, mais un record plutot qu une chaine nue : la reponse d un Refund
 * reel porte aussi un statut et une date de traitement, que le jour ou le
 * remboursement deviendra asynchrone il faudra restituer. Elargir un record ne
 * touche que la passerelle et le service ; elargir un {@code String} de retour
 * toucherait chaque appelant.</p>
 *
 * @param referenceRemboursement identifiant du Refund chez le prestataire, seul point
 *                               de rapprochement avec son extrait en cas de litige
 */
public record RemboursementCree(String referenceRemboursement) {
}
