package be.autoservplus.cookies.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format du cookie de preference (F25). L enjeu de ces tests n est pas la
 * serialisation en elle-meme mais la regle qui la gouverne : une valeur qu on ne
 * sait pas relire ne doit jamais etre interpretee comme un consentement.
 */
@DisplayName("PreferencesCookies")
class PreferencesCookiesTest {

    @Nested
    @DisplayName("Ecriture de la valeur")
    class Ecriture {

        @Test
        @DisplayName("distingue chaque finalite dans la valeur ecrite")
        void distingueChaqueFinalite() {
            assertThat(PreferencesCookies.acceptationTotale().versValeurCookie()).isEqualTo("v1-11");
            assertThat(PreferencesCookies.refusTotal().versValeurCookie()).isEqualTo("v1-00");
            assertThat(new PreferencesCookies(true, false).versValeurCookie()).isEqualTo("v1-10");
            assertThat(new PreferencesCookies(false, true).versValeurCookie()).isEqualTo("v1-01");
        }

        @Test
        @DisplayName("refuse toutes les finalites optionnelles par defaut")
        void refuseParDefaut() {
            assertThat(PreferencesCookies.refusTotal().analytique()).isFalse();
            assertThat(PreferencesCookies.refusTotal().marketing()).isFalse();
        }
    }

    @Nested
    @DisplayName("Relecture de la valeur")
    class Relecture {

        @Test
        @DisplayName("restitue exactement le choix ecrit")
        void restitueLeChoixEcrit() {
            PreferencesCookies choix = new PreferencesCookies(true, false);

            Optional<PreferencesCookies> relu =
                    PreferencesCookies.depuisValeurCookie(choix.versValeurCookie());

            assertThat(relu).contains(choix);
        }

        /**
         * Le point sensible : chacune de ces valeurs doit produire un vide, que
         * l appelant traduit par « rien n a ete choisi, on repose la question ». La
         * lire comme une acceptation reviendrait a fabriquer un consentement a partir
         * d un cookie corrompu ou perime.
         */
        @ParameterizedTest(name = "valeur inexploitable : \"{0}\"")
        @ValueSource(strings = {"", "v1-", "v1-1", "v1-111", "v2-11", "11", "v1-ab", "v1-21", "V1-11"})
        @DisplayName("traite toute valeur non conforme comme une absence de choix")
        void valeurNonConforme(String valeur) {
            assertThat(PreferencesCookies.depuisValeurCookie(valeur)).isEmpty();
        }

        @Test
        @DisplayName("traite un cookie absent comme une absence de choix")
        void cookieAbsent() {
            assertThat(PreferencesCookies.depuisValeurCookie(null)).isEmpty();
        }
    }
}
