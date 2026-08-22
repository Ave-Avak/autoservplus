package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * <b>RM-08</b> : le creneau vise n est pas (ou plus) reservable — hors plage
 * d ouverture, deja occupe, ou perdu dans une course avec un autre membre (la
 * contrainte d exclusion tranche en dernier ressort). Le code RM vit ici, en
 * Javadoc : le membre recoit le message nu, sans prefixe technique.
 */
public class CreneauIndisponibleException extends RegleMetierException {

    public CreneauIndisponibleException(String message) {
        super(message);
    }
}
