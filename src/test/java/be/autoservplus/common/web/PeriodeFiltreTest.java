package be.autoservplus.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Période d'une liste du back-office")
class PeriodeFiltreTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 20);
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Test
    @DisplayName("sans date : les trente derniers jours, aujourd'hui compris")
    void parDefaut() {
        PeriodeFiltre periode = PeriodeFiltre.de(null, null, AUJOURDHUI);

        assertThat(periode.du()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(periode.au()).isEqualTo(AUJOURDHUI);
        assertThat(periode.inversee()).isFalse();
    }

    @Test
    @DisplayName("une borne seule complète l'autre sur trente jours")
    void borneSeule() {
        assertThat(PeriodeFiltre.de(LocalDate.of(2026, 1, 1), null, AUJOURDHUI).au())
                .isEqualTo(LocalDate.of(2026, 1, 30));
        assertThat(PeriodeFiltre.de(null, LocalDate.of(2026, 1, 30), AUJOURDHUI).du())
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("début après fin : période inversée, rien n'est échangé")
    void inversee() {
        PeriodeFiltre periode = PeriodeFiltre.de(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), AUJOURDHUI);

        assertThat(periode.inversee()).isTrue();
        assertThat(periode.du()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    @DisplayName("bornes en heure de l'atelier, journée de fin comprise")
    void bornes() {
        PeriodeFiltre periode = new PeriodeFiltre(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), false);

        assertThat(periode.debut(BRUXELLES)).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
        assertThat(periode.finExclue(BRUXELLES)).isEqualTo(Instant.parse("2026-11-20T23:00:00Z"));
    }

    @Test
    @DisplayName("recherche sans date : tout l'historique ; date saisie : la période s'applique aussi")
    void rechercheSansDate() {
        PeriodeFiltre sansDate = PeriodeFiltre.de(null, null, AUJOURDHUI);
        PeriodeFiltre avecDate = PeriodeFiltre.de(LocalDate.of(2026, 11, 1), null, AUJOURDHUI);

        assertThat(sansDate.ignoreePour("CMD-2026-0001")).isTrue();
        assertThat(sansDate.ignoreePour("  ")).isFalse();
        assertThat(sansDate.debutPour("CMD-2026-0001", BRUXELLES)).isEqualTo(Instant.EPOCH);
        assertThat(sansDate.duSaisi()).isNull();
        assertThat(avecDate.ignoreePour("CMD-2026-0001")).isFalse();
        assertThat(avecDate.duSaisi()).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
