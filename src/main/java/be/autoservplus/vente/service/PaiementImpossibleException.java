package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Initiation refusee : la commande n attend pas (ou plus) de paiement — deja
 * payee, annulee (RM-21) ou remboursee. Pas de code RM en prefixe, pattern des
 * exceptions dediees du module.
 */
public class PaiementImpossibleException extends RegleMetierException {

    public PaiementImpossibleException() {
        super("Cette commande n attend pas (ou plus) de paiement.");
    }
}
