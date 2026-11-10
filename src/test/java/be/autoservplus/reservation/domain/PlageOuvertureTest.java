package be.autoservplus.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlageOuverture")
class PlageOuvertureTest {

    private static final LocalTime H8 = LocalTime.of(8, 0);
    private static final LocalTime H12 = LocalTime.of(12, 0);

    @Test
    @DisplayName("refuse une fin antérieure ou égale au début, à la création")
    void creationRefuseeSiFinAvantDebut() {
        assertThatThrownBy(() -> new PlageOuverture(DayOfWeek.MONDAY, H12, H8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlageOuverture(DayOfWeek.MONDAY, H8, H8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la modification change le jour et les heures")
    void modification() {
        PlageOuverture plage = new PlageOuverture(DayOfWeek.MONDAY, H8, H12);

        plage.modifier(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(13, 0));

        assertThat(plage.jour()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(plage.getJourSemaine()).isEqualTo((short) 6);
        assertThat(plage.getHeureDebut()).isEqualTo(LocalTime.of(9, 0));
        assertThat(plage.getHeureFin()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    @DisplayName("une modification refusée laisse la plage intacte")
    void modificationRefuseeSansEffet() {
        PlageOuverture plage = new PlageOuverture(DayOfWeek.MONDAY, H8, H12);

        assertThatThrownBy(() -> plage.modifier(DayOfWeek.TUESDAY, H12, H8))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(plage.horaire()).isEqualTo(new HorairePlage(DayOfWeek.MONDAY, H8, H12, true));
    }

    @Test
    @DisplayName("l'horaire est une copie qui ne suit pas les modifications ultérieures")
    void horaireEstUneCopie() {
        PlageOuverture plage = new PlageOuverture(DayOfWeek.MONDAY, H8, H12);
        HorairePlage avant = plage.horaire();

        plage.modifier(DayOfWeek.MONDAY, H8, LocalTime.of(10, 0));
        plage.desactiver();

        assertThat(avant).isEqualTo(new HorairePlage(DayOfWeek.MONDAY, H8, H12, true));
        assertThat(plage.horaire().actif()).isFalse();
    }
}
