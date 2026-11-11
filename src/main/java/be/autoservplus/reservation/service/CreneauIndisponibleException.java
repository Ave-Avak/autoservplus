package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * <b>RM-08</b> : le creneau vise n est pas (ou plus) reservable, hors plage
 * d ouverture, deja occupe, ou perdu dans une course avec un autre membre (la
 * contrainte d'exclusion tranche en dernier ressort, par une violation ou par un
 * interblocage). Le code RM vit ici, en
 * Javadoc : le membre recoit le message nu, sans prefixe technique.
 */
public class CreneauIndisponibleException extends RegleMetierException {

    public CreneauIndisponibleException(String message) {
        super(message);
    }

    /** Garde l'erreur de la base qui a tranché la course, pour les journaux et les tests. */
    public CreneauIndisponibleException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
