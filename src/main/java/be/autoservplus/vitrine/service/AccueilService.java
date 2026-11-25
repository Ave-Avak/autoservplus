package be.autoservplus.vitrine.service;

import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.reservation.service.DisponibiliteService;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.vitrine.web.dto.CreneauxAccueilVue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contenu calculé de la page d'accueil : un aperçu des prestations et les prochains
 * créneaux libres. Les avis viennent directement d'{@code AvisService}.
 */
@Service
@Transactional(readOnly = true)
public class AccueilService {

    /** Prestations montrées à l'accueil, une par catégorie tant qu'il en reste. */
    static final int PRESTATIONS_MONTREES = 6;
    /** Jours montrés, heures par jour, et jours parcourus au plus pour les trouver. */
    static final int JOURS_MONTRES = 3;
    static final int HEURES_PAR_JOUR = 4;
    static final int JOURS_PARCOURUS = 14;

    private final CatalogueService catalogue;
    private final DisponibiliteService disponibilites;
    private final FuseauGarage fuseau;
    private final Clock horloge;

    public AccueilService(CatalogueService catalogue, DisponibiliteService disponibilites,
                          FuseauGarage fuseau, Clock horloge) {
        this.catalogue = catalogue;
        this.disponibilites = disponibilites;
        this.fuseau = fuseau;
        this.horloge = horloge;
    }

    /**
     * Un aperçu du catalogue : la première prestation de chaque catégorie, puis les
     * suivantes si les catégories sont moins nombreuses que les places. Six vidanges
     * d'une même catégorie ne diraient rien de l'étendue de l'atelier.
     */
    public List<ArticleVue> prestationsMontrees() {
        List<ArticleVue> toutes = catalogue.prestationsActives();
        Map<String, ArticleVue> premiereParCategorie = new LinkedHashMap<>();
        toutes.forEach(prestation -> premiereParCategorie.putIfAbsent(prestation.categorie(), prestation));
        List<ArticleVue> montrees = new ArrayList<>(premiereParCategorie.values());
        toutes.stream().filter(prestation -> !montrees.contains(prestation)).forEach(montrees::add);
        return montrees.stream().limit(PRESTATIONS_MONTREES).toList();
    }

    /**
     * Prochains créneaux libres pour la plus courte des prestations au forfait, sur les
     * mêmes règles que le formulaire de réservation du membre (plages, jours fériés,
     * délai minimal, postes occupés). Vide quand le catalogue n'a aucune prestation au
     * forfait ou qu'aucun créneau ne se libère dans les deux semaines.
     */
    public Optional<CreneauxAccueilVue> prochainsCreneaux() {
        Optional<ArticleVue> reference = catalogue.prestationsActives().stream()
                .filter(prestation -> !prestation.aLHeure() && prestation.dureeMinutes() != null)
                .min(Comparator.comparing(ArticleVue::dureeMinutes));
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        ArticleVue prestation = reference.get();
        ZoneId zone = fuseau.zone();
        LocalDate jour = LocalDate.now(horloge.withZone(zone));
        List<CreneauxAccueilVue.Jour> jours = new ArrayList<>();
        for (int i = 0; i < JOURS_PARCOURUS && jours.size() < JOURS_MONTRES; i++, jour = jour.plusDays(1)) {
            List<LocalTime> heures = disponibilites.creneauxDuJour(jour, prestation.dureeMinutes()).stream()
                    .map(CreneauDisponible::debut)
                    .map(debut -> debut.atZone(zone).toLocalTime())
                    .distinct()
                    .limit(HEURES_PAR_JOUR)
                    .toList();
            if (!heures.isEmpty()) {
                jours.add(new CreneauxAccueilVue.Jour(jour, heures));
            }
        }
        return jours.isEmpty() ? Optional.empty() : Optional.of(new CreneauxAccueilVue(prestation, jours));
    }
}
