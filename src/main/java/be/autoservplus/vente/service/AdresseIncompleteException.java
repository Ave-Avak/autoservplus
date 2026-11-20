package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Aucune commande sans adresse postale complète : la facture émise au paiement doit
 * porter le nom et l'adresse du client (AR n°1, art. 5). Vérifiée côté serveur à la
 * validation, le récapitulatif ne faisant que prévenir.
 */
public class AdresseIncompleteException extends RegleMetierException {

    public AdresseIncompleteException() {
        super("Renseignez votre adresse dans votre profil avant de commander : elle figure sur la facture.");
    }
}
