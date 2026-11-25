package be.autoservplus.facturation.domain;

/**
 * Moyen d'un encaissement au comptoir (RM-20 révisée). Mollie en ligne reste le paiement
 * des commandes : il n'apparaît pas ici. Le terminal est celui du garage, quel qu'en soit
 * le fournisseur.
 */
public enum MoyenEncaissement {
    TERMINAL,
    ESPECES,
    VIREMENT
}
