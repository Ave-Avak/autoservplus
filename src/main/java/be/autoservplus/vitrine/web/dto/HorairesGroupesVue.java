package be.autoservplus.vitrine.web.dto;

import java.util.List;

/**
 * Jours consécutifs ouverts aux mêmes heures, pour le résumé des horaires du bandeau et du
 * pied de page : « Lundi au vendredi » tient sur une ligne, sept jours n'y tiennent pas.
 *
 * @param cleDebut cle i18n du premier jour
 * @param cleFin   cle i18n du dernier jour, égale à {@code cleDebut} pour un jour seul
 * @param plages   intervalles horaires, déjà formatés {@code HH:mm}
 */
public record HorairesGroupesVue(String cleDebut, String cleFin, List<String> plages) {

    public boolean unSeulJour() {
        return cleDebut.equals(cleFin);
    }
}
