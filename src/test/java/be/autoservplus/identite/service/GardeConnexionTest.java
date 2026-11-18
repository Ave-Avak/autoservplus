package be.autoservplus.identite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GardeConnexion")
class GardeConnexionTest {

    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    /** Horloge déplaçable à la main, pour franchir la fenêtre. */
    private static final class Horloge extends Clock {
        private Instant maintenant = T0;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    private final Horloge horloge = new Horloge();
    private final GardeConnexion garde = new GardeConnexion(horloge, 50, 15);

    @Test
    @DisplayName("cinquante échecs atteignent le plafond, quarante-neuf non")
    void cinquanteEchecs() {
        for (int i = 0; i < 49; i++) {
            garde.echec("203.0.113.7");
        }
        assertThat(garde.plafondAtteint("203.0.113.7")).isFalse();

        garde.echec("203.0.113.7");

        assertThat(garde.plafondAtteint("203.0.113.7")).isTrue();
        assertThat(garde.plafondAtteint("203.0.113.8")).isFalse();
    }

    @Test
    @DisplayName("le plafond se lève quand les échecs sortent de la fenêtre de quinze minutes")
    void fenetre() {
        for (int i = 0; i < 50; i++) {
            garde.echec("203.0.113.7");
        }

        horloge.maintenant = T0.plus(Duration.ofMinutes(15)).plusNanos(1);

        assertThat(garde.plafondAtteint("203.0.113.7")).isFalse();
    }

    @Test
    @DisplayName("les adresses IPv6 d'un même /64 partagent un seul plafond")
    void ipv6ParSlash64() {
        for (int i = 1; i <= 50; i++) {
            garde.echec("2001:db8:1:2::" + Integer.toHexString(i));
        }

        assertThat(garde.plafondAtteint("2001:db8:1:2:ffff:ffff:ffff:ffff")).isTrue();
        assertThat(garde.plafondAtteint("2001:db8:1:3::1")).isFalse();
    }

    @ParameterizedTest(name = "{0} donne {1}")
    @CsvSource({
            "203.0.113.7, 203.0.113.7",
            "2001:db8:1:2::1, 20010db800010002/64",
            "2001:DB8:1:2:aaaa:bbbb:cccc:dddd, 20010db800010002/64",
            "[2001:db8:1:2::1], 20010db800010002/64",
            "fe80::1%eth0, fe80000000000000/64",
            "::ffff:192.0.2.1, 192.0.2.1",
            "pas-une-ip.exemple, pas-une-ip.exemple"
    })
    @DisplayName("clé de comptage d'une adresse")
    void cleIp(String ip, String attendue) {
        assertThat(GardeConnexion.cleIp(ip)).isEqualTo(attendue);
    }

    @Test
    @DisplayName("une adresse absente a sa propre clé, sans exception")
    void ipAbsente() {
        assertThat(GardeConnexion.cleIp(null)).isEqualTo("?");
        assertThat(GardeConnexion.cleIp(" ")).isEqualTo("?");
    }
}
