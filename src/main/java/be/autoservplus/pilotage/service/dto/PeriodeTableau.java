package be.autoservplus.pilotage.service.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Période du tableau de bord (A25), bornes comprises, en jours de l'atelier.
 *
 * <p>Trois raccourcis, le jour, la semaine et le mois en cours, ou un intervalle saisi.
 * Sans rien de saisi, le mois en cours : c'est l'écran d'arrivée du garage. La semaine
 * commence le lundi, quelle que soit la langue de l'écran : le chiffre d'une semaine ne
 * doit pas changer quand l'administrateur passe de l'anglais au français.</p>
 *
 * <p>La semaine et le mois sont pris en entier, jours à venir compris : les rendez-vous
 * déjà pris pour la fin du mois font partie de ce que le garage veut lire.</p>
 *
 * @param raccourci raccourci retenu, nul pour un intervalle saisi
 */
public record PeriodeTableau(LocalDate du, LocalDate au, Raccourci raccourci) {

    /** Les trois vues demandées par le cahier des charges. */
    public enum Raccourci {
        JOUR, SEMAINE, MOIS;

        /** Valeur portée par l'adresse : {@code ?periode=semaine}. */
        public String code() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Raccourci lu(String code) {
            for (Raccourci raccourci : values()) {
                if (raccourci.code().equals(code)) {
                    return raccourci;
                }
            }
            return MOIS;
        }
    }

    /**
     * Une date saisie l'emporte sur le raccourci. Une borne seule est complétée : « du »
     * seul court jusqu'à aujourd'hui, « au » seul part du premier jour de son mois.
     */
    public static PeriodeTableau de(String raccourci, LocalDate du, LocalDate au, LocalDate aujourdhui) {
        if (du != null || au != null) {
            LocalDate fin = au != null ? au : (du.isAfter(aujourdhui) ? du : aujourdhui);
            LocalDate debut = du != null ? du : au.withDayOfMonth(1);
            return new PeriodeTableau(debut, fin, null);
        }
        Raccourci retenu = Raccourci.lu(raccourci);
        return switch (retenu) {
            case JOUR -> new PeriodeTableau(aujourdhui, aujourdhui, retenu);
            case SEMAINE -> new PeriodeTableau(
                    aujourdhui.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    aujourdhui.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), retenu);
            case MOIS -> new PeriodeTableau(aujourdhui.withDayOfMonth(1),
                    aujourdhui.with(TemporalAdjusters.lastDayOfMonth()), retenu);
        };
    }

    /** Début après fin : les indicateurs sont à zéro et l'écran le dit, sans échanger les bornes. */
    public boolean inversee() {
        return du.isAfter(au);
    }

    public Instant debut(ZoneId zone) {
        return du.atStartOfDay(zone).toInstant();
    }

    /** Lendemain de {@code au} à minuit : la journée de fin est comprise. */
    public Instant finExclue(ZoneId zone) {
        return au.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** Les jours de la période, vide si elle est inversée. */
    public List<LocalDate> jours() {
        return inversee() ? List.of() : du.datesUntil(au.plusDays(1)).toList();
    }

    /** Code du raccourci pour l'adresse et le gabarit, nul pour un intervalle saisi. */
    public String code() {
        return raccourci == null ? null : raccourci.code();
    }

    /** Dates saisies, pour le formulaire : nulles sous un raccourci. */
    public LocalDate duSaisi() {
        return raccourci == null ? du : null;
    }

    public LocalDate auSaisi() {
        return raccourci == null ? au : null;
    }
}
