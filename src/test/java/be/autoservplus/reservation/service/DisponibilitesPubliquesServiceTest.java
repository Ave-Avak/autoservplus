package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.reservation.service.dto.DisponibilitesPubliques;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DisponibilitesPubliquesService")
class DisponibilitesPubliquesServiceTest {

    /** Lundi 14 septembre 2026, 08:00 UTC. */
    private static final Instant DEPART = Instant.parse("2026-09-14T08:00:00Z");

    private final DisponibiliteService disponibilites = mock(DisponibiliteService.class);
    private final PrestationRepository prestations = mock(PrestationRepository.class);
    private final ParametreAtelierRepository parametres = mock(ParametreAtelierRepository.class);
    private final HorlogeReglable horloge = new HorlogeReglable(DEPART);

    private DisponibilitesPubliquesService service;
    private final Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);

    /** Horloge qu'un test avance à la main, pour franchir la fraîcheur de la mémoire. */
    private static final class HorlogeReglable extends Clock {
        private Instant instant;

        HorlogeReglable(Instant instant) { this.instant = instant; }
        void avancer(Duration duree) { instant = instant.plus(duree); }
        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @BeforeEach
    void setUp() {
        when(parametres.courants()).thenReturn(new ParametreAtelier());
        when(disponibilites.creneauxDuJour(any(), anyInt())).thenReturn(List.of());
        service = new DisponibilitesPubliquesService(disponibilites, prestations, parametres, horloge);
    }

    private UUID prestationEnBase(int minutes, boolean active, boolean aLHeure) {
        Prestation prestation = new Prestation(entretien, "P" + minutes, "Prestation", new BigDecimal("10.00"), minutes);
        if (!active) {
            prestation.desactiver();
        }
        Prestation espionne = org.mockito.Mockito.spy(prestation);
        when(espionne.estALHeure()).thenReturn(aLHeure);
        UUID reference = UUID.randomUUID();
        when(prestations.findByReference(reference)).thenReturn(Optional.of(espionne));
        return reference;
    }

    @Test
    @DisplayName("30 jours à partir d'aujourd'hui, dans le fuseau du garage, sans poste")
    void trenteJours() {
        LocalDate aujourdhui = LocalDate.of(2026, 9, 14);
        Instant neufHeures = Instant.parse("2026-09-15T07:00:00Z");
        when(disponibilites.creneauxDuJour(eq(aujourdhui.plusDays(1)), anyInt())).thenReturn(List.of(
                new CreneauDisponible(neufHeures, neufHeures.plus(Duration.ofMinutes(30)), null, 2)));

        DisponibilitesPubliques resultat = service.prochainsJours(List.of());

        assertThat(resultat.premierJour()).isEqualTo(aujourdhui);
        assertThat(resultat.dernierJour()).isEqualTo(aujourdhui.plusDays(29));
        assertThat(resultat.creneaux()).containsExactly(
                new DisponibilitesPubliques.Creneau(neufHeures, neufHeures.plus(Duration.ofMinutes(30))));
        verify(disponibilites, times(30)).creneauxDuJour(any(), eq(30));
    }

    @Test
    @DisplayName("sans prestation, la durée du pas, avec des prestations, la somme arrondie au pas")
    void duree() {
        UUID vidange = prestationEnBase(45, true, false);
        UUID filtre = prestationEnBase(20, true, false);

        assertThat(service.prochainsJours(List.of()).duree()).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.prochainsJours(List.of(vidange, filtre, vidange)).duree()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    @DisplayName("une prestation inconnue, retirée ou à l'heure répond comme introuvable")
    void prestationsNonReservables() {
        UUID retiree = prestationEnBase(30, false, false);
        UUID aLHeure = prestationEnBase(60, true, true);
        UUID inconnue = UUID.randomUUID();
        when(prestations.findByReference(inconnue)).thenReturn(Optional.empty());

        for (UUID reference : List.of(retiree, aLHeure, inconnue)) {
            assertThatThrownBy(() -> service.prochainsJours(List.of(reference)))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Test
    @DisplayName("le calcul est gardé 60 secondes par durée, puis refait")
    void memoire() {
        service.prochainsJours(List.of());
        horloge.avancer(Duration.ofSeconds(59));
        service.prochainsJours(List.of());
        verify(disponibilites, times(30)).creneauxDuJour(any(), anyInt());

        horloge.avancer(Duration.ofSeconds(1));
        service.prochainsJours(List.of());
        verify(disponibilites, times(60)).creneauxDuJour(any(), anyInt());
    }

    @Test
    @DisplayName("deux durées différentes ont chacune leur calcul")
    void uneEntreeParDuree() {
        UUID longue = prestationEnBase(120, true, false);

        service.prochainsJours(List.of());
        service.prochainsJours(List.of(longue));

        verify(disponibilites, times(30)).creneauxDuJour(any(), eq(30));
        verify(disponibilites, times(30)).creneauxDuJour(any(), eq(120));
    }
}
