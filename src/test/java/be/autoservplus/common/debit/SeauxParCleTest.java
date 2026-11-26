package be.autoservplus.common.debit;

import static org.assertj.core.api.Assertions.assertThat;

import be.autoservplus.common.debit.SeauxParCle.Decision;
import be.autoservplus.common.debit.SeauxParCle.Limite;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SeauxParCle")
class SeauxParCleTest {

    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-11-26T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
        void avancer(Duration duree) { maintenant = maintenant.plus(duree); }
    }

    private final Horloge horloge = new Horloge();

    @Test
    @DisplayName("accepte jusqu'à la limite, puis refuse avec le délai avant le prochain jeton")
    void limite() {
        SeauxParCle seaux = new SeauxParCle(List.of(new Limite(5, Duration.ofMinutes(15))), 100, horloge);
        for (int i = 0; i < 5; i++) {
            assertThat(seaux.consommer("a").acceptee()).isTrue();
        }
        Decision refus = seaux.consommer("a");
        assertThat(refus.acceptee()).isFalse();
        assertThat(refus.saturee()).isFalse();
        assertThat(refus.attente()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("un jeton revient au rythme de la limite, sans attendre la période entière")
    void remplissageContinu() {
        SeauxParCle seaux = new SeauxParCle(List.of(new Limite(5, Duration.ofMinutes(15))), 100, horloge);
        for (int i = 0; i < 5; i++) {
            seaux.consommer("a");
        }
        horloge.avancer(Duration.ofMinutes(3));
        assertThat(seaux.consommer("a").acceptee()).isTrue();
        assertThat(seaux.consommer("a").acceptee()).isFalse();
    }

    @Test
    @DisplayName("chaque clé a son propre seau")
    void parCle() {
        SeauxParCle seaux = new SeauxParCle(List.of(new Limite(1, Duration.ofMinutes(1))), 100, horloge);
        assertThat(seaux.consommer("a").acceptee()).isTrue();
        assertThat(seaux.consommer("a").acceptee()).isFalse();
        assertThat(seaux.consommer("b").acceptee()).isTrue();
    }

    @Test
    @DisplayName("deux limites : la plus stricte sur la durée l'emporte")
    void deuxLimites() {
        SeauxParCle seaux = new SeauxParCle(List.of(
                new Limite(10, Duration.ofMinutes(1)), new Limite(12, Duration.ofHours(1))), 100, horloge);
        int acceptees = 0;
        for (int minute = 0; minute < 5; minute++) {
            for (int i = 0; i < 10; i++) {
                if (seaux.consommer("a").acceptee()) {
                    acceptees++;
                }
            }
            horloge.avancer(Duration.ofMinutes(1));
        }
        assertThat(acceptees).isLessThanOrEqualTo(12 + 1);
    }

    @Test
    @DisplayName("carte pleine : les clés inactives sont retirées, sinon la décision dit saturée")
    void saturation() {
        SeauxParCle seaux = new SeauxParCle(List.of(new Limite(1, Duration.ofMinutes(10))), 2, horloge);
        seaux.consommer("a");
        seaux.consommer("b");
        Decision pleine = seaux.consommer("c");
        assertThat(pleine.saturee()).isTrue();
        assertThat(pleine.acceptee()).isFalse();

        horloge.avancer(Duration.ofMinutes(10));
        assertThat(seaux.consommer("c").acceptee()).isTrue();
        assertThat(seaux.nombreDeCles()).isEqualTo(1);
    }
}
