package be.autoservplus.vente.domain;

import be.autoservplus.reservation.domain.JoursFeries;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Délai de rétractation d'un bien, compté en jours depuis sa remise au membre (CDE,
 * art. VI.47, § 2, 2°).
 *
 * <p>Le règlement 1182/71, auquel renvoie le considérant 41 de la directive 2011/83/UE,
 * fixe le calcul : le jour de la remise ne compte pas, le délai commence le lendemain
 * et expire à la fin du quatorzième jour. Si ce jour est un samedi, un dimanche ou un
 * jour férié, le délai expire à la fin du jour ouvrable suivant. Les jours fériés sont
 * les dix jours légaux belges, ceux qui ferment l'atelier.</p>
 *
 * <p>Calcul en jours et non en durée de 14 fois 24 heures, contrairement aux
 * prestations dont le délai part d'un instant, la conclusion du contrat : la remise est
 * saisie comme un jour.</p>
 */
public final class DelaiRetractationBien {

    public static final int JOURS = 14;

    private DelaiRetractationBien() {
    }

    /** Dernier jour où le membre peut encore se rétracter. */
    public static LocalDate dernierJour(LocalDate remise) {
        LocalDate jour = remise.plusDays(JOURS);
        while (!ouvrable(jour)) {
            jour = jour.plusDays(1);
        }
        return jour;
    }

    /** Le délai est échu le lendemain de son dernier jour. */
    public static boolean expire(LocalDate remise, LocalDate aujourdhui) {
        return aujourdhui.isAfter(dernierJour(remise));
    }

    private static boolean ouvrable(LocalDate jour) {
        return jour.getDayOfWeek() != DayOfWeek.SATURDAY
                && jour.getDayOfWeek() != DayOfWeek.SUNDAY
                && !JoursFeries.estFerie(jour);
    }
}
