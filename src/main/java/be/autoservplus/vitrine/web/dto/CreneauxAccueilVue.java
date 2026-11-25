package be.autoservplus.vitrine.web.dto;

import be.autoservplus.catalogue.service.dto.ArticleVue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Prochains créneaux libres montrés à l'accueil, pour une prestation de référence.
 *
 * @param prestation la prestation dont la durée a servi au calcul
 * @param jours      les premiers jours qui ont au moins un créneau, dans l'ordre
 */
public record CreneauxAccueilVue(ArticleVue prestation, List<Jour> jours) {

    /** Un jour et ses premières heures de départ libres, à l'heure du garage. */
    public record Jour(LocalDate date, List<LocalTime> heures) {
    }
}
