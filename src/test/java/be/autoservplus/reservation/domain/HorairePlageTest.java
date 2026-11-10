package be.autoservplus.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HorairePlage")
class HorairePlageTest {

    private static final HorairePlage LUNDI_8_12 =
            new HorairePlage(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), true);

    @ParameterizedTest(name = "[{0}, {1}) : contient {2}, recoupe {3}")
    @CsvSource({
            "07:00, 08:00, false, false",
            "07:30, 08:30, false, true",
            "08:00, 09:00, true,  true",
            "11:00, 12:00, true,  true",
            "11:30, 12:30, false, true",
            "12:00, 13:00, false, false"
    })
    @DisplayName("contient et recoupe des intervalles semi-ouverts")
    void contientEtRecoupe(String debut, String fin, boolean contient, boolean recoupe) {
        LocalTime d = LocalTime.parse(debut);
        LocalTime f = LocalTime.parse(fin);

        assertThat(LUNDI_8_12.contient(DayOfWeek.MONDAY, d, f)).isEqualTo(contient);
        assertThat(LUNDI_8_12.recoupe(DayOfWeek.MONDAY, d, f)).isEqualTo(recoupe);
    }

    @Test
    @DisplayName("un autre jour n'est ni contenu ni recoupé")
    void autreJour() {
        assertThat(LUNDI_8_12.contient(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
        assertThat(LUNDI_8_12.recoupe(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
    }

    @Test
    @DisplayName("une plage inactive ne couvre rien")
    void inactive() {
        HorairePlage inactive = new HorairePlage(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), false);

        assertThat(inactive.contient(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
        assertThat(inactive.recoupe(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
    }
}
