package be.autoservplus.communication.service;

/**
 * Éléments de l'avis au garage d'une commande payée (F14). Chaînes plates, déjà
 * formatées : le module communication ne dépend pas du module vente.
 *
 * @param numeroCommande   numéro lisible de la commande (CMD-...)
 * @param montantTvac      montant encaissé, formaté en euros TVAC
 * @param membre           nom complet du membre
 * @param prestations      la commande porte des prestations à planifier, et non des
 *                         pièces à préparer. Une commande ne mélange pas les deux.
 * @param ruptureAHonorer  le stock ne couvrait pas toute la commande au paiement
 */
public record DetailsCommandeGarage(String numeroCommande,
                                    String montantTvac,
                                    String membre,
                                    boolean prestations,
                                    boolean ruptureAHonorer) {
}
