package be.autoservplus.vente.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Délai de rétractation d'un bien, depuis sa remise")
class DelaiRetractationBienTest {

    @Test
    @DisplayName("quatorzième jour ouvrable : le délai y expire, le jour de remise ne compte pas")
    void quatorziemeJourOuvrable() {
        // Remise le lundi 2 novembre 2026 : le délai court du 3 au lundi 16.
        assertThat(DelaiRetractationBien.dernierJour(LocalDate.of(2026, 11, 2)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    @DisplayName("quatorze jours font deux semaines : remise un jeudi, dernier jour un jeudi")
    void memeJourDeLaSemaine() {
        // Le quatorzième jour tombe toujours le même jour de la semaine que la remise :
        // seul un samedi de remise donne un dernier jour un samedi.
        assertThat(DelaiRetractationBien.dernierJour(LocalDate.of(2026, 11, 5)))
                .isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    @DisplayName("quatorzième jour un samedi : prolongé au lundi")
    void samedi() {
        assertThat(DelaiRetractationBien.dernierJour(LocalDate.of(2026, 10, 31)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    @DisplayName("quatorzième jour férié (11 novembre) : prolongé au lendemain ouvrable")
    void jourFerie() {
        assertThat(DelaiRetractationBien.dernierJour(LocalDate.of(2026, 10, 28)))
                .isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    @DisplayName("Noël un vendredi puis le week-end : prolongé au lundi 28 décembre")
    void ferieSuiviDuWeekEnd() {
        assertThat(DelaiRetractationBien.dernierJour(LocalDate.of(2026, 12, 11)))
                .isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    @DisplayName("échu le lendemain du dernier jour, pas avant")
    void echeance() {
        LocalDate remise = LocalDate.of(2026, 11, 2);

        assertThat(DelaiRetractationBien.expire(remise, LocalDate.of(2026, 11, 16))).isFalse();
        assertThat(DelaiRetractationBien.expire(remise, LocalDate.of(2026, 11, 17))).isTrue();
    }
}
