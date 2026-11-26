package be.autoservplus.common.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Polices des documents PDF")
class PolicesPdfTest {

    private static Stream<BaseFont> polices() {
        return Stream.of(PolicesPdf.NORMALE, PolicesPdf.GRASSE, PolicesPdf.ITALIQUE);
    }

    @Test
    @DisplayName("chiffres tabulaires : « 1 » et « 0 » ont la même largeur, dans les trois styles")
    void chiffresTabulaires() {
        polices().forEach(police -> {
            int largeurDuZero = police.getWidth('0');
            assertThat("123456789".chars().map(police::getWidth))
                    .as("chiffres de %s", police.getPostscriptFontName())
                    .containsOnly(largeurDuZero);
            assertThat(police.getWidth('1')).isEqualTo(largeurDuZero);
        });
    }

    @Test
    @DisplayName("couverture du français, du néerlandais et de l'anglais, espace fine et euro compris")
    void couverture() {
        String caracteres = "éèêëàâçîïôûùüœÉÈÀÇÔĳ\u2019\u201C\u201D\u201E\u2026\u20AC\u00A0\u202F";
        polices().forEach(police -> caracteres.codePoints().forEach(c ->
                assertThat(police.charExists(c)).as("U+%04X", c).isTrue()));
    }

    @Test
    @DisplayName("le style choisit le fichier, sans gras ni italique simulés")
    void styleChoisitLeFichier() {
        assertThat(PolicesPdf.police(9, Font.BOLD, Color.BLACK).getBaseFont()).isSameAs(PolicesPdf.GRASSE);
        assertThat(PolicesPdf.police(9, Font.ITALIC, Color.BLACK).getBaseFont()).isSameAs(PolicesPdf.ITALIQUE);
        assertThat(PolicesPdf.police(9, Font.NORMAL, Color.BLACK).getBaseFont()).isSameAs(PolicesPdf.NORMALE);
        assertThat(PolicesPdf.police(9, Font.BOLD, Color.BLACK).getStyle()).isEqualTo(Font.NORMAL);
    }
}
