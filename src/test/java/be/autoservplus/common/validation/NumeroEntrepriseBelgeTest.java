package be.autoservplus.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les numéros valides sont calculés pour l'occasion (clé 97 moins le reste des huit
 * premiers chiffres) et ne sont recopiés d'aucune entreprise.
 */
@DisplayName("Numéro d'entreprise belge et numéro de TVA")
class NumeroEntrepriseBelgeTest {

    @Nested
    @DisplayName("Numéro d'entreprise")
    class Bce {

        @ParameterizedTest
        @ValueSource(strings = {"0400000086", "0400.000.086", "0400 000 086", " 0400.000.086 ", "1000000021"})
        @DisplayName("valide, avec ou sans points ni espaces, premier chiffre 0 ou 1")
        void valide(String numero) {
            assertThat(NumeroEntrepriseBelge.estValide(numero)).isTrue();
        }

        @Test
        @DisplayName("clé fausse : le numéro de démonstration, qui attend 49 et porte 89")
        void cleFausse() {
            assertThat(NumeroEntrepriseBelge.estValide("0123.456.789")).isFalse();
            assertThat(NumeroEntrepriseBelge.estValide("0400.000.087")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"040000008", "04000000860", "0400.000.08"})
        @DisplayName("longueur fausse")
        void longueurFausse(String numero) {
            assertThat(NumeroEntrepriseBelge.estValide(numero)).isFalse();
        }

        @Test
        @DisplayName("premier chiffre hors 0 et 1, même avec une clé juste")
        void premierChiffre() {
            // 20000000 modulo 97 vaut 55, la clé juste est donc 42.
            assertThat(NumeroEntrepriseBelge.estValide("2000000042")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "BE0400000086", "04OO000086"})
        @DisplayName("absent, vide ou portant autre chose que des chiffres")
        void horsForme(String numero) {
            assertThat(NumeroEntrepriseBelge.estValide(numero)).isFalse();
        }
    }

    @Nested
    @DisplayName("Numéro de TVA")
    class Tva {

        @ParameterizedTest
        @ValueSource(strings = {"BE0400000086", "BE0400.000.086", "BE 0400 000 086", "be0400000086", "Be 0400.000.086"})
        @DisplayName("« BE » suivi des dix chiffres du numéro d'entreprise, quelle que soit la casse")
        void correspond(String tva) {
            assertThat(NumeroEntrepriseBelge.tvaCorrespond(tva, "0400.000.086")).isTrue();
        }

        @Test
        @DisplayName("incohérent avec le numéro d'entreprise")
        void incoherent() {
            assertThat(NumeroEntrepriseBelge.tvaCorrespond("BE1000000021", "0400.000.086")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0400000086", "FR0400000086", "BE04000000861"})
        @DisplayName("préfixe absent ou étranger, ou chiffres en trop")
        void horsForme(String tva) {
            assertThat(NumeroEntrepriseBelge.tvaCorrespond(tva, "0400.000.086")).isFalse();
        }

        @Test
        @DisplayName("numéro d'entreprise mal formé : aucune TVA ne peut lui correspondre")
        void bceMalForme() {
            assertThat(NumeroEntrepriseBelge.tvaCorrespond("BE040000008", "040000008")).isFalse();
            assertThat(NumeroEntrepriseBelge.tvaCorrespond("BE0400000086", null)).isFalse();
            assertThat(NumeroEntrepriseBelge.tvaCorrespond(null, "0400000086")).isFalse();
        }
    }
}
