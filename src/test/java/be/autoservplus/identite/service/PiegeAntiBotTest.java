package be.autoservplus.identite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controles passifs : champ piege et delai minimal.
 *
 * <p>Horloge figee : le delai minimal est une duree, et le tester avec une horloge
 * systeme reviendrait a faire attendre le test ou a le rendre sensible a la charge de
 * la machine.</p>
 */
@DisplayName("PiegeAntiBot")
class PiegeAntiBotTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-20T12:00:00Z");
    private final PiegeAntiBot pieges =
            new PiegeAntiBot(Clock.fixed(MAINTENANT, ZoneOffset.UTC), true);

    /** Horodatage vieux de {@code secondes}, tel qu un formulaire l aurait porte. */
    private static String ilYa(long secondes) {
        return String.valueOf(MAINTENANT.minusSeconds(secondes).toEpochMilli());
    }

    @Nested
    @DisplayName("champ piège")
    class ChampPiege {

        @Test
        @DisplayName("rempli : soumission écartée")
        void rempliEcarte() {
            assertThat(pieges.soumissionAutomatique("ACME SA", ilYa(30))).isTrue();
        }

        @Test
        @DisplayName("vide ou absent : soumission acceptée")
        void videAccepte() {
            assertThat(pieges.soumissionAutomatique("", ilYa(30))).isFalse();
            assertThat(pieges.soumissionAutomatique(null, ilYa(30))).isFalse();
            assertThat(pieges.soumissionAutomatique("   ", ilYa(30))).isFalse();
        }
    }

    @Nested
    @DisplayName("délai minimal")
    class Delai {

        @Test
        @DisplayName("soumission instantanée : écartée")
        void instantaneeEcartee() {
            assertThat(pieges.soumissionAutomatique(null, ilYa(0))).isTrue();
        }

        @Test
        @DisplayName("au-delà du seuil : acceptée")
        void auDelaDuSeuilAcceptee() {
            assertThat(pieges.soumissionAutomatique(null,
                    ilYa(PiegeAntiBot.DELAI_MINIMAL.toSeconds() + 1))).isFalse();
        }

        /**
         * Un horodatage absent, illisible ou situe dans le futur ne vient pas d un
         * formulaire servi par ce site : il a ete fabrique. Le traiter comme « assez
         * ancien » laisserait passer la falsification la plus evidente.
         */
        @Test
        @DisplayName("absent, illisible ou dans le futur : écarté")
        void horodatageFabriqueEcarte() {
            assertThat(pieges.soumissionAutomatique(null, null)).isTrue();
            assertThat(pieges.soumissionAutomatique(null, "")).isTrue();
            assertThat(pieges.soumissionAutomatique(null, "bonjour")).isTrue();
            assertThat(pieges.soumissionAutomatique(null,
                    String.valueOf(MAINTENANT.plusSeconds(60).toEpochMilli()))).isTrue();
        }
    }

    @Test
    @DisplayName("désactivé par propriété : tout passe")
    void desactivable() {
        PiegeAntiBot eteint = new PiegeAntiBot(Clock.fixed(MAINTENANT, ZoneOffset.UTC), false);

        assertThat(eteint.soumissionAutomatique("ACME SA", ilYa(0))).isFalse();
    }
}
