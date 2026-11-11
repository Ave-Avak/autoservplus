package be.autoservplus.reservation.service;

import java.sql.SQLException;

/**
 * Lecture de l'état SQL d'une erreur de la base. Le planning et la réservation ne
 * traduisent en refus métier que la violation d'une contrainte d'exclusion et, pour
 * la réservation, l'interblocage par lequel PostgreSQL peut trancher la même course :
 * toute autre erreur est un défaut, qui doit remonter tel quel au lieu d'être présenté
 * comme un conflit de créneau.
 */
final class EtatSql {

    /** Violation d'une contrainte d'exclusion. */
    static final String EXCLUSION = "23P01";

    /** Interblocage (deadlock_detected). */
    static final String INTERBLOCAGE = "40P01";

    private EtatSql() {
    }

    /** Premier état SQL trouvé dans la chaîne des causes, ou {@code null}. */
    static String de(Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    static boolean estUneExclusion(Throwable erreur) {
        return EXCLUSION.equals(de(erreur));
    }

    static boolean estUnInterblocage(Throwable erreur) {
        return INTERBLOCAGE.equals(de(erreur));
    }
}
