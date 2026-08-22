package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * F24 : aucune commande sans acceptation des conditions generales de vente.
 * Verifiee cote serveur — la case cochee du navigateur n est qu un confort,
 * un POST forge sans le parametre est refuse ici.
 */
public class CgvNonAccepteesException extends RegleMetierException {

    public CgvNonAccepteesException() {
        super("Les conditions generales de vente doivent etre acceptees pour commander.");
    }
}
