package be.autoservplus.common.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Période d'une liste du back-office, bornes comprises, en jours de l'atelier.
 *
 * <p>Sans date saisie, la liste couvre les trente derniers jours, aujourd'hui compris :
 * l'écran s'ouvre sur l'activité récente au lieu de tout l'historique, qui grossit
 * chaque jour. Une borne seule complète l'autre par la même règle.</p>
 *
 * <p>Une recherche sans date saisie porte sur tout l'historique : qui cherche un numéro
 * de commande ou une adresse ne sait pas forcément de quand elle date, et la période par
 * défaut lui cacherait le résultat. Dès qu'une date est saisie, la période s'applique
 * aussi à la recherche.</p>
 *
 * @param parDefaut aucune date n'a été saisie : la période vient de la règle des trente jours
 */
public record PeriodeFiltre(LocalDate du, LocalDate au, boolean parDefaut) {

    public static final int JOURS_PAR_DEFAUT = 30;

    /** Borne haute d'une recherche sans période, au-delà de toute date réelle. */
    private static final Instant FIN_DES_TEMPS =
            LocalDate.of(9999, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

    public static PeriodeFiltre de(LocalDate du, LocalDate au, LocalDate aujourdhui) {
        LocalDate fin = au != null ? au : (du != null ? du.plusDays(JOURS_PAR_DEFAUT - 1) : aujourdhui);
        LocalDate debut = du != null ? du : fin.minusDays(JOURS_PAR_DEFAUT - 1);
        return new PeriodeFiltre(debut, fin, du == null && au == null);
    }

    /** Début après fin : la liste est vide et l'écran le dit, plutôt que d'échanger les bornes. */
    public boolean inversee() {
        return du.isAfter(au);
    }

    /** La recherche porte sur tout l'historique : aucune date saisie, un terme saisi. */
    public boolean ignoreePour(String recherche) {
        return parDefaut && recherche != null && !recherche.isBlank();
    }

    /** Date saisie, pour le formulaire et les liens de pagination : nulle sans saisie. */
    public LocalDate duSaisi() {
        return parDefaut ? null : du;
    }

    public LocalDate auSaisi() {
        return parDefaut ? null : au;
    }

    public Instant debut(ZoneId zone) {
        return du.atStartOfDay(zone).toInstant();
    }

    /** Lendemain de {@code au} à minuit : la journée de fin est comprise. */
    public Instant finExclue(ZoneId zone) {
        return au.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** Borne basse de la requête : la période, ou rien pour une recherche sans date. */
    public Instant debutPour(String recherche, ZoneId zone) {
        return ignoreePour(recherche) ? Instant.EPOCH : debut(zone);
    }

    public Instant finPour(String recherche, ZoneId zone) {
        return ignoreePour(recherche) ? FIN_DES_TEMPS : finExclue(zone);
    }
}
