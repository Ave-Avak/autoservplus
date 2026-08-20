package be.autoservplus.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ParametreAtelier")
class ParametreAtelierTest {

    private ParametreAtelier parametres() {
        return new ParametreAtelier();
    }

    @Test
    @DisplayName("porte les valeurs par defaut de la migration")
    void valeursParDefaut() {
        ParametreAtelier p = parametres();
        assertThat(p.zone()).isEqualTo(ZoneId.of("Europe/Brussels"));
        assertThat(p.pas()).isEqualTo(Duration.ofMinutes(30));
        assertThat(p.tampon()).isEqualTo(Duration.ofMinutes(10));
        assertThat(p.delaiMinimal()).isEqualTo(Duration.ofHours(24));
        assertThat(p.horizon()).isEqualTo(Duration.ofDays(60));
        assertThat(p.delaiAnnulation()).isEqualTo(Duration.ofHours(24));
        assertThat(p.isConfirmationAutomatique()).isFalse();
        assertThat(p.getMaxRdvEnAttenteParMembre()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("accepte une modification dans les bornes")
    void modificationValide() {
        ParametreAtelier p = parametres();
        p.modifier("Europe/Paris", 15, 0, 2, 90, 48, true, 5);

        assertThat(p.pas()).isEqualTo(Duration.ofMinutes(15));
        assertThat(p.tampon()).isZero();
        assertThat(p.delaiMinimal()).isEqualTo(Duration.ofHours(2));
        assertThat(p.horizon()).isEqualTo(Duration.ofDays(90));
        assertThat(p.delaiAnnulation()).isEqualTo(Duration.ofHours(48));
        assertThat(p.isConfirmationAutomatique()).isTrue();
        assertThat(p.getMaxRdvEnAttenteParMembre()).isEqualTo((short) 5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 20, 90})
    @DisplayName("refuse un pas hors de la liste admise")
    void refuseUnPasInvalide(int pas) {
        assertThatThrownBy(() -> parametres().modifier("Europe/Brussels", pas, 10, 24, 60, 24, false, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuse un fuseau inconnu")
    void refuseUnFuseauInconnu() {
        assertThatThrownBy(() -> parametres().modifier("Mars/Olympus", 30, 10, 24, 60, 24, false, 3))
                .isInstanceOf(java.time.DateTimeException.class);
    }

    @Test
    @DisplayName("refuse les valeurs hors bornes")
    void refuseHorsBornes() {
        assertThatThrownBy(() -> parametres().modifier("Europe/Brussels", 30, 121, 24, 60, 24, false, 3))
                .hasMessageContaining("tampon");
        assertThatThrownBy(() -> parametres().modifier("Europe/Brussels", 30, 10, 169, 60, 24, false, 3))
                .hasMessageContaining("delai minimal");
        assertThatThrownBy(() -> parametres().modifier("Europe/Brussels", 30, 10, 24, 0, 24, false, 3))
                .hasMessageContaining("horizon");
        assertThatThrownBy(() -> parametres().modifier("Europe/Brussels", 30, 10, 24, 60, 169, false, 3))
                .hasMessageContaining("annulation");
        assertThatThrownBy(() -> parametres().modifier("Europe/Brussels", 30, 10, 24, 60, 24, false, 0))
                .hasMessageContaining("en attente");
    }

    @Test
    @DisplayName("une modification refusee ne change rien")
    void modificationAtomique() {
        ParametreAtelier p = parametres();
        assertThatThrownBy(() -> p.modifier("Europe/Paris", 15, 10, 24, 60, 24, true, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(p.zone()).isEqualTo(ZoneId.of("Europe/Brussels"));
        assertThat(p.pas()).isEqualTo(Duration.ofMinutes(30));
        assertThat(p.isConfirmationAutomatique()).isFalse();
    }
}