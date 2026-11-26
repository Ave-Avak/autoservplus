package be.autoservplus.vitrine.service;

import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.reservation.service.DisponibiliteService;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.support.FuseauxDeTest;
import be.autoservplus.vitrine.web.dto.CreneauxAccueilVue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Contenu calculé de l'accueil")
class AccueilServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    /** Mercredi 25 novembre 2026, 23 h 30 en UTC, déjà jeudi 0 h 30 à Bruxelles : le jour se lit au garage. */
    private static final Instant MAINTENANT = Instant.parse("2026-11-25T23:30:00Z");

    private final CatalogueService catalogue = mock(CatalogueService.class);
    private final DisponibiliteService disponibilites = mock(DisponibiliteService.class);
    private final AccueilService service = new AccueilService(catalogue, disponibilites, FuseauxDeTest.bruxelles(),
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private static ArticleVue prestation(String libelle, String categorie, Integer duree) {
        return new ArticleVue(UUID.randomUUID(), libelle, libelle, null, categorie,
                new BigDecimal("50.00"), new BigDecimal("60.50"), duree, null, true);
    }

    private static CreneauDisponible a(LocalDate jour, int heure, int minute) {
        Instant debut = jour.atTime(heure, minute).atZone(BRUXELLES).toInstant();
        return new CreneauDisponible(debut, debut.plusSeconds(1800), null, 1);
    }

    private static ArticleVue alHeure(String libelle) {
        return new ArticleVue(UUID.randomUUID(), libelle, libelle, null, "Atelier", new BigDecimal("65.00"),
                new BigDecimal("78.65"), null, null, true, null, null, null, "HEURE", null, null, null);
    }

    @Test
    @DisplayName("aperçu : une prestation par catégorie d'abord, puis les suivantes, six au plus, aucune à l'heure")
    void prestationsMontrees() {
        List<ArticleVue> toutes = List.of(
                prestation("Vidange", "Entretien", 30), prestation("Révision", "Entretien", 120),
                prestation("Plaquettes", "Freinage", 60), prestation("Disques", "Freinage", 90),
                prestation("Pneus", "Pneumatique", 45), prestation("Recharge", "Climatisation", 45),
                prestation("Diagnostic", "Diagnostic", 30), alHeure("Main-d'œuvre"),
                prestation("Courroie", "Moteur", 180));
        when(catalogue.prestationsActives()).thenReturn(toutes);

        assertThat(service.prestationsMontrees()).extracting(ArticleVue::libelle)
                .containsExactly("Vidange", "Plaquettes", "Pneus", "Recharge", "Diagnostic", "Courroie");
    }

    @Test
    @DisplayName("aperçu : les prestations choisies par le garage, dans son ordre, sans prestation à l'heure")
    void prestationsChoisies() {
        when(catalogue.prestationsDeLaVitrine()).thenReturn(List.of(
                prestation("Vidange", "Entretien", 30), alHeure("Main-d'œuvre"), prestation("Révision", "Entretien", 90)));

        assertThat(service.prestationsMontrees()).extracting(ArticleVue::libelle).containsExactly("Vidange", "Révision");
        verify(catalogue, never()).prestationsActives();
    }

    @Test
    @DisplayName("créneaux : la première prestation de l'accueil, trois jours qui en ont, quatre heures par jour")
    void prochainsCreneaux() {
        ArticleVue courte = prestation("Contrôle", "Diagnostic", 15);
        when(catalogue.prestationsDeLaVitrine()).thenReturn(List.of(courte, prestation("Vidange", "Entretien", 30)));
        LocalDate jeudi = LocalDate.of(2026, 11, 26);
        when(disponibilites.creneauxDuJour(any(), eq(15))).thenReturn(List.of());
        when(disponibilites.creneauxDuJour(eq(jeudi.plusDays(1)), eq(15))).thenReturn(
                IntStream.range(8, 14).mapToObj(h -> a(jeudi.plusDays(1), h, 0)).toList());
        when(disponibilites.creneauxDuJour(eq(jeudi.plusDays(3)), eq(15))).thenReturn(
                List.of(a(jeudi.plusDays(3), 9, 0), a(jeudi.plusDays(3), 9, 0)));
        when(disponibilites.creneauxDuJour(eq(jeudi.plusDays(9)), eq(15))).thenReturn(
                List.of(a(jeudi.plusDays(9), 14, 30)));
        when(disponibilites.creneauxDuJour(eq(jeudi.plusDays(10)), eq(15))).thenReturn(
                List.of(a(jeudi.plusDays(10), 10, 0)));

        CreneauxAccueilVue creneaux = service.prochainsCreneaux().orElseThrow();

        assertThat(creneaux.prestation()).isEqualTo(courte);
        assertThat(creneaux.jours()).extracting(CreneauxAccueilVue.Jour::date)
                .containsExactly(jeudi.plusDays(1), jeudi.plusDays(3), jeudi.plusDays(9));
        assertThat(creneaux.jours().get(0).heures())
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(creneaux.jours().get(1).heures()).as("une heure de départ commune à deux postes, une fois")
                .containsExactly(LocalTime.of(9, 0));
        verify(disponibilites).creneauxDuJour(jeudi, 15);
        verify(disponibilites, never()).creneauxDuJour(jeudi.plusDays(10), 15);
    }

    @Test
    @DisplayName("aucun créneau en deux semaines, ou aucune prestation au forfait : rien n'est montré")
    void aucunCreneau() {
        when(catalogue.prestationsActives()).thenReturn(List.of(prestation("Main-d'œuvre", "Atelier", null)));
        assertThat(service.prochainsCreneaux()).isEmpty();
        verify(disponibilites, never()).creneauxDuJour(any(), anyInt());

        when(catalogue.prestationsActives()).thenReturn(List.of(prestation("Vidange", "Entretien", 30)));
        when(disponibilites.creneauxDuJour(any(), anyInt())).thenReturn(List.of());
        assertThat(service.prochainsCreneaux()).isEmpty();
    }
}
