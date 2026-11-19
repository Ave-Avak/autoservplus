package be.autoservplus.legal.service;

import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les fichiers de la notice sont ceux de la Commission, à l'octet près (SVG), ou leur
 * seule page 1 (PDF). Une empreinte qui change signale un fichier retouché, ce que
 * l'annexe I du règlement (UE) 2025/1960 interdit. Sources dans {@code THIRD-PARTY.md}.
 */
@DisplayName("Fichiers de la notice sur la garantie légale")
class NoticeGarantieFichiersTest {

    private static final Path RESSOURCES = Path.of("src", "main", "resources");

    private static String empreinte(Path fichier) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fichier)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "static/images/notice-garantie/notice-garantie-fr.svg, cadb74fc6249e6698bea0d19cfd689d5da809e83be314863a09b41597a73dd74",
            "static/images/notice-garantie/notice-garantie-nl.svg, 510c45f1844038c43650bbc68467deec273402701c7bd6922725eab996d0d15c",
            "static/images/notice-garantie/notice-garantie-en.svg, d1b4293b75637022b582b3025f789d292e1c845660a0460d855bbe523b9763f7",
            "notice-garantie/notice-garantie-fr.pdf, f506bd086fddec29ffbf333c5376e20adccd2a5c7dd60951342b0224fb201ee4",
            "notice-garantie/notice-garantie-nl.pdf, ca3d7bd19a6645e8aa60d3d3588de3fbff1c6c68f7ee6e349cac646022d06d10",
            "notice-garantie/notice-garantie-en.pdf, 398aff804d907a57a0c1f77fa572689d31eb0378bbac07fcb18a885e957499ca"})
    @DisplayName("empreinte SHA-256 inchangée")
    void empreinteInchangee(String chemin, String attendue) throws Exception {
        assertThat(empreinte(RESSOURCES.resolve(chemin))).isEqualTo(attendue);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"fr", "nl", "en"})
    @DisplayName("le PDF joint ne porte que la page en couleur")
    void unePage(String langue) throws Exception {
        PdfReader lecteur = new PdfReader(new NoticeGarantie().pdf(Locale.of(langue)));
        try {
            assertThat(lecteur.getNumberOfPages()).isEqualTo(1);
        } finally {
            lecteur.close();
        }
    }

    @ParameterizedTest(name = "{0} donne {1}")
    @CsvSource({"fr, fr", "nl, nl", "en, en", "de, fr"})
    @DisplayName("langue de la notice, le français à défaut")
    void langue(String demandee, String servie) {
        NoticeGarantie notice = new NoticeGarantie();

        assertThat(notice.langue(Locale.of(demandee))).isEqualTo(servie);
        assertThat(notice.image(Locale.of(demandee)))
                .isEqualTo("/images/notice-garantie/notice-garantie-" + servie + ".svg");
        assertThat(notice.nomFichierPdf(Locale.of(demandee))).isEqualTo("notice-garantie-" + servie + ".pdf");
    }

    @Test
    @DisplayName("sans locale, la notice française")
    void sansLocale() {
        assertThat(new NoticeGarantie().langue(null)).isEqualTo("fr");
    }
}
