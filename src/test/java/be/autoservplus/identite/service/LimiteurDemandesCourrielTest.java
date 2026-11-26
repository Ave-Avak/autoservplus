package be.autoservplus.identite.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comportement du limiteur des formulaires qui envoient un courriel, indépendant de son
 * mécanisme de comptage.
 */
@DisplayName("LimiteurDemandesCourriel")
class LimiteurDemandesCourrielTest {

    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-11-26T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
        void avancer(Duration duree) { maintenant = maintenant.plus(duree); }
    }

    private static final String IP = "203.0.113.7";

    private final Horloge horloge = new Horloge();
    private final LimiteurDemandesCourriel limiteur = new LimiteurDemandesCourriel(horloge, 5, 20, 15);

    @Test
    @DisplayName("cinq demandes par adresse, la sixième est refusée")
    void plafondParAdresse() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiteur.autoriser("alex@exemple.be", IP)).isTrue();
        }
        assertThat(limiteur.autoriser("alex@exemple.be", IP)).isFalse();
        assertThat(limiteur.autoriser("autre@exemple.be", IP)).isTrue();
    }

    @Test
    @DisplayName("la casse et les espaces n'ouvrent pas un second quota")
    void adresseNormalisee() {
        for (int i = 0; i < 5; i++) {
            limiteur.autoriser(" Alex@Exemple.BE", IP);
        }
        assertThat(limiteur.autoriser("alex@exemple.be", "198.51.100.1")).isFalse();
    }

    @Test
    @DisplayName("vingt demandes par IP, quelle que soit l'adresse")
    void plafondParIp() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiteur.autoriser("a" + i + "@exemple.be", IP)).isTrue();
        }
        assertThat(limiteur.autoriser("nouvelle@exemple.be", IP)).isFalse();
        assertThat(limiteur.autoriser("nouvelle@exemple.be", "198.51.100.1")).isTrue();
    }

    @Test
    @DisplayName("une demande refusée sur l'adresse consomme quand même le budget de l'IP")
    void deuxCompteursConsommes() {
        for (int i = 0; i < 20; i++) {
            limiteur.autoriser("cible@exemple.be", IP);
        }
        assertThat(limiteur.autoriser("autre@exemple.be", IP)).isFalse();
    }

    @Test
    @DisplayName("une adresse nulle ou une IP inconnue sont comptées comme les autres")
    void valeursAbsentes() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiteur.autoriser(null, null)).isTrue();
        }
        assertThat(limiteur.autoriser(null, null)).isFalse();
    }

    @Test
    @DisplayName("l'inscription ne consomme que le budget de l'IP")
    void inscriptionParIp() {
        for (int i = 0; i < 5; i++) {
            limiteur.autoriser("alex@exemple.be", IP);
        }
        assertThat(limiteur.autoriserParIp("198.51.100.1")).isTrue();
        for (int i = 0; i < 15; i++) {
            assertThat(limiteur.autoriserParIp(IP)).isTrue();
        }
        assertThat(limiteur.autoriserParIp(IP)).isFalse();
    }

    @Test
    @DisplayName("passé la fenêtre, le budget est entier")
    void budgetRendu() {
        for (int i = 0; i < 20; i++) {
            limiteur.autoriser("alex@exemple.be", IP);
        }
        horloge.avancer(Duration.ofMinutes(15).plusSeconds(1));
        for (int i = 0; i < 5; i++) {
            assertThat(limiteur.autoriser("alex@exemple.be", IP)).isTrue();
        }
    }

    @Test
    @DisplayName("deux IPv6 du même réseau /64 partagent le budget de l'IP")
    void ipv6ParReseau() {
        for (int i = 0; i < 20; i++) {
            limiteur.autoriserParIp("2001:db8:1:2::" + Integer.toHexString(i + 1));
        }
        assertThat(limiteur.autoriserParIp("2001:db8:1:2:abcd::1")).isFalse();
        assertThat(limiteur.autoriserParIp("2001:db8:1:3::1")).isTrue();
    }

    @Test
    @DisplayName("l'avis au titulaire ne consomme que le budget de l'adresse (AUD-69)")
    void avisSansBudgetIp() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiteur.autoriserAdresse("alex@exemple.be")).isTrue();
        }
        assertThat(limiteur.autoriserAdresse("alex@exemple.be")).isFalse();
        for (int i = 0; i < 20; i++) {
            assertThat(limiteur.autoriserParIp(IP)).isTrue();
        }
    }

    @Test
    @DisplayName("carte des IP pleine : la demande est refusée (AUD-70)")
    void carteDesIpPleine() {
        LimiteurDemandesCourriel petit = new LimiteurDemandesCourriel(horloge, 5, 20, 15, 2);
        assertThat(petit.autoriserParIp("203.0.113.1")).isTrue();
        assertThat(petit.autoriserParIp("203.0.113.2")).isTrue();
        assertThat(petit.autoriserParIp("203.0.113.3")).isFalse();
    }

    @Test
    @DisplayName("carte des adresses pleine : le plafond par IP décide seul")
    void carteDesAdressesPleine() {
        LimiteurDemandesCourriel petit = new LimiteurDemandesCourriel(horloge, 5, 20, 15, 2);
        assertThat(petit.autoriser("a@exemple.be", IP)).isTrue();
        assertThat(petit.autoriser("b@exemple.be", "198.51.100.1")).isTrue();
        assertThat(petit.autoriser("c@exemple.be", IP)).isTrue();
    }
}
