package be.autoservplus.common.temps;

import be.autoservplus.support.FuseauxDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fuseau du garage")
class FuseauGarageTest {

    /** 23 h 30 UTC le 31 décembre : 0 h 30 le 1er janvier à Bruxelles. */
    private static final Instant NUIT_DU_NOUVEL_AN = Instant.parse("2026-12-31T23:30:00Z");

    private final FuseauGarage fuseau = FuseauxDeTest.bruxelles();

    @Test
    @DisplayName("un fait de la nuit est daté du jour du garage, pas de celui d'UTC")
    void jourAuGarage() {
        assertThat(fuseau.jour(NUIT_DU_NOUVEL_AN)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(fuseau.heureLocale(NUIT_DU_NOUVEL_AN)).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 30));
        assertThat(fuseau.annee(NUIT_DU_NOUVEL_AN)).isEqualTo(2027);
    }

    @Test
    @DisplayName("l'heure d'été est appliquée : deux heures d'écart en juillet")
    void heureDEte() {
        assertThat(fuseau.heureLocale(Instant.parse("2026-07-14T22:30:00Z")))
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 0, 30));
    }

    @Test
    @DisplayName("un instant absent reste absent")
    void instantAbsent() {
        assertThat(fuseau.jour(null)).isNull();
        assertThat(fuseau.heureLocale(null)).isNull();
    }
}
