package be.autoservplus.reservation.service.dto;

import be.autoservplus.reservation.domain.PlageOuverture;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Plage d'ouverture vue du back-office. */
public record PlageVue(Long id, DayOfWeek jour, LocalTime heureDebut, LocalTime heureFin, boolean actif) {

    public static PlageVue de(PlageOuverture plage) {
        return new PlageVue(plage.getId(), plage.jour(), plage.getHeureDebut(), plage.getHeureFin(),
                plage.isActif());
    }

    public String cleJour() {
        return cleJour(jour);
    }

    /**
     * Clé de message du jour. Écrite en toutes lettres plutôt que dérivée de
     * {@code DayOfWeek.name()} : la dérivation produirait des clés anglaises
     * ({@code vitrine.jour.monday}) au milieu d'un fichier de messages entièrement
     * français, et le jour où une clé manquerait, l'erreur serait illisible.
     */
    public static String cleJour(DayOfWeek jour) {
        return switch (jour) {
            case MONDAY -> "vitrine.jour.lundi";
            case TUESDAY -> "vitrine.jour.mardi";
            case WEDNESDAY -> "vitrine.jour.mercredi";
            case THURSDAY -> "vitrine.jour.jeudi";
            case FRIDAY -> "vitrine.jour.vendredi";
            case SATURDAY -> "vitrine.jour.samedi";
            case SUNDAY -> "vitrine.jour.dimanche";
        };
    }
}
