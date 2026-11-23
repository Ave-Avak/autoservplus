package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Langue négociée sur Accept-Language")
class LanguesSupporteesTest {

    @ParameterizedTest(name = "« {0} » donne {1}")
    @CsvSource(delimiter = '|', value = {
            "nl | nl",
            "nl-BE | nl",
            "en-GB, en;q=0.9 | en",
            "de, nl;q=0.8, fr;q=0.5 | nl",
            "fr;q=0.2, en;q=0.9 | en",
            "de, es | fr",
            "* | fr"})
    @DisplayName("la première langue admise dans l'ordre de préférence du client")
    void ordreDePreference(String entete, String attendue) {
        assertThat(LanguesSupportees.negociee(entete).getLanguage()).isEqualTo(attendue);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "???", "fr;q=deux"})
    @DisplayName("un en-tête absent ou illisible donne le français, sans erreur")
    void enteteAbsentOuIllisible(String entete) {
        assertThat(LanguesSupportees.negociee(entete)).isEqualTo(LanguesSupportees.DEFAUT);
    }

    @Test
    @DisplayName("la langue rendue est l'une des trois du site, jamais une variante régionale")
    void langueAdmiseSeulement() {
        assertThat(LanguesSupportees.admises()).contains(LanguesSupportees.negociee("nl-NL"));
    }
}
