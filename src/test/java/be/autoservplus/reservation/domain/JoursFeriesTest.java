package be.autoservplus.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Jours fériés légaux belges")
class JoursFeriesTest {

    @ParameterizedTest(name = "Pâques {0} : {1}")
    @CsvSource({
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28",
            "2038, 2038-04-25",
            "2285, 2285-03-22"
    })
    @DisplayName("dimanche de Pâques, y compris les dates extrêmes du 22 mars et du 25 avril")
    void paques(int annee, LocalDate attendu) {
        assertThat(JoursFeries.paques(annee)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("les dix jours de 2026")
    void annee2026() {
        assertThat(JoursFeries.de(2026)).containsExactly(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 14),
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 7, 21),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 12, 25));
    }

    @Test
    @DisplayName("estFerie suit la liste, Pâques lui-même et les fêtes régionales n'en sont pas")
    void estFerie() {
        JoursFeries.de(2027).forEach(jour -> assertThat(JoursFeries.estFerie(jour)).as(jour.toString()).isTrue());
        assertThat(JoursFeries.estFerie(LocalDate.of(2027, 3, 28))).isFalse();
        assertThat(JoursFeries.estFerie(LocalDate.of(2026, 9, 27))).isFalse();
        assertThat(JoursFeries.estFerie(LocalDate.of(2026, 7, 11))).isFalse();
        assertThat(JoursFeries.estFerie(LocalDate.of(2026, 11, 12))).isFalse();
    }
}
