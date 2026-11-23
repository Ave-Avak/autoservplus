package be.autoservplus.vente.domain;

import be.autoservplus.reservation.domain.JoursFeries;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Délai de rétractation, compté en jours depuis son point de départ : la remise d'un bien
 * au membre, la conclusion du contrat pour une prestation (CDE, art. VI.47, § 2).
 *
 * <p>Le règlement (CEE, Euratom) n° 1182/71, article 3, auquel renvoie le considérant 41
 * de la directive 2011/83/UE, fixe le calcul : le jour du départ ne compte pas, le délai
 * commence le lendemain et expire à la dernière heure du quatorzième jour. Si ce jour est
 * un samedi, un dimanche ou un jour férié, le délai expire à la fin du jour ouvrable
 * suivant. Les jours fériés sont les dix jours légaux belges, ceux qui ferment l'atelier.</p>
 *
 * <p>Calcul en jours et non en durée de 14 fois 24 heures : une prestation commandée un
 * lundi à 9 h reste rétractable jusqu'à la fin du lundi quatorze jours plus tard, et non
 * jusqu'à 9 h. Les jours sont ceux du garage, l'appelant convertit l'instant.</p>
 */
public final class DelaiRetractation {

    public static final int JOURS = 14;

    private DelaiRetractation() {
    }

    /** Dernier jour où le membre peut encore se rétracter. */
    public static LocalDate dernierJour(LocalDate depart) {
        LocalDate jour = depart.plusDays(JOURS);
        while (!ouvrable(jour)) {
            jour = jour.plusDays(1);
        }
        return jour;
    }

    /** Le délai est échu le lendemain de son dernier jour. */
    public static boolean expire(LocalDate depart, LocalDate aujourdhui) {
        return aujourdhui.isAfter(dernierJour(depart));
    }

    private static boolean ouvrable(LocalDate jour) {
        return jour.getDayOfWeek() != DayOfWeek.SATURDAY
                && jour.getDayOfWeek() != DayOfWeek.SUNDAY
                && !JoursFeries.estFerie(jour);
    }
}
