package be.autoservplus.facturation.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.facturation.service.dto.DocumentAvoir;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identification de l'émetteur sur la note de crédit, lue dans le PDF lui-même. Mêmes
 * cas que {@link GenerateurPdfFactureTest} : l'avoir imprime le même en-tête et le même
 * pied, et une mention obligatoire perdue sur l'un ne se verrait pas sur l'autre.
 */
@DisplayName("GenerateurPdfAvoir")
class GenerateurPdfAvoirTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage(
            "Garage Démo", "Garage de démonstration", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
            "0123.456.789", "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034",
            "+32 2 000 00 00", "garage@demo.test");

    /** Un garage tenu par une personne physique : ni forme juridique, ni registre. */
    private static final IdentiteGarage PERSONNE_PHYSIQUE = new IdentiteGarage(
            "Garage Démo", "Jean Dupont", "",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
            "0123.456.789", "", "BE0123456789", "BE68 5390 0754 7034",
            "+32 2 000 00 00", "garage@demo.test");

    private static MessageSource messageSourceReel() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static DocumentAvoir avoir() {
        List<DocumentFacture.LigneFacture> lignes = List.of(
                new DocumentFacture.LigneFacture("Plaquettes de frein avant", 1,
                        new BigDecimal("100.00"), new BigDecimal("21.00"), new BigDecimal("100.00")));
        VentilationTva ventilation = new VentilationTva(List.of(
                new VentilationTva.TrancheTva(new BigDecimal("21"),
                        new BigDecimal("100.00"), new BigDecimal("21.00"))));
        return new DocumentAvoir("AV-2026-0001", Instant.parse("2026-08-30T10:00:00Z"),
                "2026-0042", Instant.parse("2026-08-22T14:30:00Z"), "CMD-2026-0107",
                "RETRACTATION_F30",
                new DocumentFacture.ClientFacture("Marie Dupont", "Avenue Louise", "250",
                        "1050", "Ixelles", "Belgique", "marie@exemple.be"),
                lignes, ventilation,
                new BigDecimal("100.00"), new BigDecimal("21.00"), new BigDecimal("121.00"),
                Locale.FRENCH);
    }

    /** Texte du PDF, blancs normalisés : chaque fin de ligne devient une espace. */
    private static String texteDu(byte[] pdf) throws IOException {
        PdfReader lecteur = new PdfReader(pdf);
        try {
            StringBuilder texte = new StringBuilder();
            PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
            for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
                texte.append(extracteur.getTextFromPage(page)).append('\n');
            }
            return texte.toString().replaceAll("\\s+", " ");
        } finally {
            lecteur.close();
        }
    }

    @Test
    @DisplayName("porte l'identification légale complète du garage : forme juridique, RPM, BCE, TVA, siège, IBAN")
    void mentionsDuGarage() throws Exception {
        String texte = texteDu(new GenerateurPdfAvoir(messageSourceReel(), GARAGE).engendrer(avoir()));

        assertThat(texte)
                .contains("AV-2026-0001")
                .contains("Garage de démonstration SRL")
                .contains("RPM Bruxelles")
                .contains("Rue de l'Atelier 12, 1000 Bruxelles")
                .contains("0123.456.789")
                .contains("BE0123456789")
                .contains("BE68 5390 0754 7034");
    }

    @Test
    @DisplayName("personne physique : ni forme juridique ni mention RPM, et rien à leur place")
    void personnePhysique() throws Exception {
        String texte = texteDu(new GenerateurPdfAvoir(messageSourceReel(), PERSONNE_PHYSIQUE)
                .engendrer(avoir()));

        assertThat(texte)
                .contains("Jean Dupont, BCE : 0123.456.789, TVA : BE0123456789")
                .contains("Jean Dupont Rue de l'Atelier")
                .contains("BCE : 0123.456.789 TVA : BE0123456789")
                .doesNotContain("RPM");
    }
}
