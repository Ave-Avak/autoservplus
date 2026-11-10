package be.autoservplus.reservation.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Horaire d'une plage d'ouverture à un instant donné, en heure locale du garage.
 *
 * <p>Sert à comparer l'état d'une plage avant et après une modification par le
 * back-office : un rendez-vous que l'ancienne plage couvrait et qu'aucune plage active
 * ne couvre plus est en conflit (A24). La comparaison se fait sur des heures locales,
 * comme le calcul des disponibilités : un rendez-vous est projeté dans le fuseau du
 * garage avant d'être comparé, jamais l'inverse.</p>
 */
public record HorairePlage(DayOfWeek jour, LocalTime debut, LocalTime fin, boolean actif) {

    /** Vrai si [debutRdv, finRdv) tient entièrement dans la plage, un jour donné. */
    public boolean contient(DayOfWeek jourRdv, LocalTime debutRdv, LocalTime finRdv) {
        return actif && jour == jourRdv && !debutRdv.isBefore(debut) && !finRdv.isAfter(fin);
    }

    /** Vrai si [debutRdv, finRdv) recoupe la plage, un jour donné. */
    public boolean recoupe(DayOfWeek jourRdv, LocalTime debutRdv, LocalTime finRdv) {
        return actif && jour == jourRdv && debutRdv.isBefore(fin) && finRdv.isAfter(debut);
    }
}
