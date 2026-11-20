package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * <b>RM-08</b> : le creneau vise n est pas (ou plus) reservable, hors plage
 * d ouverture, deja occupe, ou perdu dans une course avec un autre membre (la
 * contrainte d'exclusion tranche en dernier ressort, par une violation ou par un
 * interblocage). Le code RM vit ici, en
 * Javadoc : le membre recoit le message nu, sans prefixe technique.
 */
public class CreneauIndisponibleException extends RefusTraduit {

    /** Le créneau n'est pas dans une plage ouverte, ou il tombe hors des délais de réservation. */
    public static final String NON_OUVERT = "rdv.creneau.non-ouvert";
    /** Aucun poste n'est plus libre sur ce créneau au moment de l'enregistrement. */
    public static final String PRIS = "rdv.creneau.pris";
    /** Le garage déplace un rendez-vous sur un créneau où aucun poste n'est libre. */
    public static final String AUCUN_POSTE = "rdv.creneau.aucun-poste";
    /** Un autre membre a gagné la course sur le même intervalle. */
    public static final String PRIS_PAR_UN_AUTRE = "rdv.creneau.pris-par-un-autre";

    public CreneauIndisponibleException(String cleMessage, String message) {
        super(cleMessage, message);
    }

    /** Garde l'erreur de la base qui a tranché la course, pour les journaux et les tests. */
    public CreneauIndisponibleException(String cleMessage, String message, Throwable cause) {
        super(cleMessage, message);
        initCause(cause);
    }
}
