package be.autoservplus.reservation.domain;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;
import java.util.TreeSet;

/**
 * Les dix jours fériés légaux belges (loi du 4 janvier 1974), calculés et non saisis.
 *
 * <p>Sept dates sont fixes. Les trois autres suivent Pâques : lundi de Pâques,
 * Ascension (Pâques + 39 jours) et lundi de Pentecôte (Pâques + 50 jours). Pâques se
 * calcule par l'algorithme grégorien de Meeus, Jones et Butcher, exact pour toute année
 * du calendrier grégorien. Une table de dates saisies à la main aurait dû être
 * prolongée chaque année.</p>
 *
 * <p>Un jour férié ferme tout l'atelier en V1 : un garage ne peut pas choisir d'ouvrir
 * ce jour-là. Les fêtes régionales ou communautaires (27 septembre, 11 juillet,
 * 15 novembre) ne sont pas des jours fériés légaux et n'y figurent pas, un garage qui
 * les chôme les saisit comme indisponibilités.</p>
 */
public final class JoursFeries {

    private static final Set<MonthDay> FIXES = Set.of(
            MonthDay.of(1, 1),    // Nouvel An
            MonthDay.of(5, 1),    // Fête du travail
            MonthDay.of(7, 21),   // Fête nationale
            MonthDay.of(8, 15),   // Assomption
            MonthDay.of(11, 1),   // Toussaint
            MonthDay.of(11, 11),  // Armistice
            MonthDay.of(12, 25)); // Noël

    private JoursFeries() {
    }

    public static boolean estFerie(LocalDate jour) {
        if (FIXES.contains(MonthDay.from(jour))) {
            return true;
        }
        LocalDate paques = paques(jour.getYear());
        return jour.equals(paques.plusDays(1))
                || jour.equals(paques.plusDays(39))
                || jour.equals(paques.plusDays(50));
    }

    /** Les dix jours fériés de l'année, dans l'ordre du calendrier. */
    public static Set<LocalDate> de(int annee) {
        Set<LocalDate> jours = new TreeSet<>();
        FIXES.forEach(fixe -> jours.add(fixe.atYear(annee)));
        LocalDate paques = paques(annee);
        jours.add(paques.plusDays(1));
        jours.add(paques.plusDays(39));
        jours.add(paques.plusDays(50));
        return jours;
    }

    /** Dimanche de Pâques, algorithme grégorien anonyme (Meeus, Jones, Butcher). */
    static LocalDate paques(int annee) {
        int a = annee % 19;
        int b = annee / 100;
        int c = annee % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mois = (h + l - 7 * m + 114) / 31;
        int jour = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(annee, mois, jour);
    }
}
