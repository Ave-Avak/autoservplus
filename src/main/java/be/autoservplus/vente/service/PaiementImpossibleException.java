package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Initiation refusée : la commande n'attend pas (ou plus) de paiement, déjà
 * payée, annulée (RM-21) ou remboursée. Pas de code RM en préfixe, pattern des
 * exceptions dédiées du module.
 */
public class PaiementImpossibleException extends RegleMetierException {

    public PaiementImpossibleException() {
        super("Cette commande n attend pas (ou plus) de paiement.");
    }
}
