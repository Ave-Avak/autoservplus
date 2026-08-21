package be.autoservplus.reservation.service.support;

import be.autoservplus.reservation.domain.StatutRdv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sorties de {@link FormatageRdv}. Les formats sont partages entre l UI membre, l UI
 * admin et le courriel : un changement ici propage partout, donc figer les
 * chaines attendues sert de garde-fou.
 */
@DisplayName("FormatageRdv")
class FormatageRdvTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final Instant DIMANCHE_10H =
            LocalDate.of(2026, 9, 13).atTime(10, 0).atZone(BRUXELLES).toInstant();

    @Nested
    @DisplayName("jour et heure")
    class DateEtHeure {

        @Test
        @DisplayName("jour lisible en francais avec le fuseau applique")
        void jourLisible() {
            assertThat(FormatageRdv.jourLisible(DIMANCHE_10H, BRUXELLES))
                    .isEqualTo("dimanche 13 septembre 2026");
        }

        @Test
        @DisplayName("heure au format HH:mm")
        void heureLisible() {
            assertThat(FormatageRdv.heureLisible(DIMANCHE_10H, BRUXELLES))
                    .isEqualTo("10:00");
        }

        @Test
        @DisplayName("le fuseau argument change le jour rendu")
        void fuseauInfluenceLeJour() {
            // 2026-09-13 00:30 Bruxelles = 2026-09-12 22:30 UTC : selon le fuseau
            // choisi, le jour lisible peut basculer d une date a l autre.
            Instant t = LocalDate.of(2026, 9, 13).atTime(0, 30).atZone(BRUXELLES).toInstant();
            assertThat(FormatageRdv.jourLisible(t, BRUXELLES)).isEqualTo("dimanche 13 septembre 2026");
            assertThat(FormatageRdv.jourLisible(t, ZoneId.of("UTC"))).isEqualTo("samedi 12 septembre 2026");
        }
    }

    @Nested
    @DisplayName("euros")
    class Euros {

        // NumberFormat belge insere un espace INSECABLE (U+00A0) entre le montant
        // et le symbole €. On l ecrit explicitement dans l attendu pour eviter
        // qu une lecture visuelle laisse croire a un espace ordinaire.
        private static final char NBSP = '\u00A0';

        @Test
        @DisplayName("format belge avec virgule et symbole €")
        void formatBelge() {
            assertThat(FormatageRdv.euros(new BigDecimal("49.00")))
                    .isEqualTo("49,00" + NBSP + "€");
        }

        @Test
        @DisplayName("zero et arrondi a deux decimales")
        void variations() {
            assertThat(FormatageRdv.euros(new BigDecimal("0.00")))
                    .isEqualTo("0,00" + NBSP + "€");
            assertThat(FormatageRdv.euros(new BigDecimal("9.5")))
                    .isEqualTo("9,50" + NBSP + "€");
        }
    }

    @Nested
    @DisplayName("statut lisible")
    class StatutLisible {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "EN_ATTENTE, En attente de confirmation",
                "CONFIRME,   Confirmé",
                "REFUSE,     Refusé par le garage",
                "ANNULE,     Annulé",
                "HONORE,     Effectué",
                "ABSENT,     Non présenté"
        })
        @DisplayName("chaque statut a un libelle stable")
        void libelleParStatut(StatutRdv statut, String attendu) {
            assertThat(FormatageRdv.statutLisible(statut)).isEqualTo(attendu);
        }
    }
}
