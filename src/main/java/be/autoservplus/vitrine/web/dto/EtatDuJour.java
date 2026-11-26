package be.autoservplus.vitrine.web.dto;

import be.autoservplus.reservation.service.dto.PlageVue;

import java.time.DayOfWeek;

/**
 * Ce que le bandeau du site dit de l'ouverture du garage, à l'instant de la page.
 *
 * <p>Quatre cas, chacun avec son message. {@code OUVERT} : jusqu'à {@code heure}, fin de
 * la plage en cours. {@code PAUSE} : fermé entre deux plages du même jour, réouverture à
 * {@code heure}. {@code FERME} : prochaine ouverture le {@code jour} à {@code heure},
 * aujourd'hui plus tard, demain ou un autre jour. {@code FERME_LONGTEMPS} : aucune
 * ouverture dans les quinze jours, le bandeau renvoie aux horaires.</p>
 *
 * @param jourDOuverture jour de la prochaine ouverture, nul quand le garage est ouvert
 * @param dansNJours     0 aujourd'hui, 1 demain, au-delà le nom du jour est affiché
 * @param exceptionnel   vrai quand aujourd'hui aurait dû être ouvert (jour férié,
 *                       fermeture de tout l'atelier) : le visiteur qui connaît les
 *                       horaires habituels sait pourquoi la porte est close
 */
public record EtatDuJour(Etat etat, String heure, DayOfWeek jourDOuverture, int dansNJours,
                         boolean exceptionnel) {

    public enum Etat { OUVERT, PAUSE, FERME, FERME_LONGTEMPS }

    /** Clé du message du bandeau, choisie selon l'état et l'échéance. */
    public String cleMessage() {
        return switch (etat) {
            case OUVERT -> "site.etat.ouvert";
            case PAUSE -> "site.etat.pause";
            case FERME_LONGTEMPS -> "site.etat.ferme";
            case FERME -> dansNJours == 0 ? "site.etat.ouvre.aujourdhui"
                    : dansNJours == 1 ? "site.etat.ouvre.demain" : "site.etat.ouvre.jour";
        };
    }

    /**
     * Clé du nom du jour dans une phrase, sous {@code site.etat.} : minuscule en français
     * et en néerlandais (« ouvre lundi »), capitale en anglais, quand les noms de
     * {@code vitrine.jour.} commencent tous par une capitale pour les tableaux.
     */
    public String cleJour() {
        return jourDOuverture == null ? null
                : PlageVue.cleJour(jourDOuverture).replace("vitrine.jour.", "site.etat.jour.");
    }
}
