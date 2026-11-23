package be.autoservplus.legal.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.config.MentionsIdentiteGarage;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.dto.TexteArchiveVue;
import be.autoservplus.support.FuseauxDeTest;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Conditions générales acceptées, en PDF")
class ConditionsGeneralesPdfTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage(
            "Garage Démo", "Garage de démonstration", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
            "0123.456.789", "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034",
            "+32 2 000 00 00", "garage@demo.test");
    private static final Instant EFFET = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant ACCEPTATION = Instant.parse("2026-11-18T09:30:00Z");
    private static final String TEXTE = String.join("\n",
            "Conditions générales de vente",
            "Les présentes conditions régissent la vente.",
            "Article 1. Vendeur",
            "Le vendeur est le garage identifié ci-dessous.",
            "Article 2. Champ d'application et acceptation",
            "Toute commande suppose l'acceptation des présentes conditions.");

    private final VersionsDocumentsService versions = mock(VersionsDocumentsService.class);
    private final ConditionsGeneralesPdf pdf;

    ConditionsGeneralesPdfTest() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        pdf = new ConditionsGeneralesPdf(versions, new MentionsIdentiteGarage(GARAGE, messages), messages,
                FuseauxDeTest.bruxelles());
    }

    private static String texte(byte[] document) throws IOException {
        PdfReader lecteur = new PdfReader(document);
        StringBuilder texte = new StringBuilder();
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            texte.append(extracteur.getTextFromPage(page)).append('\n');
        }
        return texte.toString();
    }

    @Test
    @DisplayName("texte gelé de la version, sa mention et celle de l'acceptation, identité sous l'article 1")
    void versionAcceptee() throws IOException {
        when(versions.archive(TypeDocumentVersionne.CGV, "CGV-2026-04", Locale.FRENCH))
                .thenReturn(Optional.of(new TexteArchiveVue("CGV-2026-04", EFFET, "fr", TEXTE, true,
                        List.of("fr", "nl", "en"))));

        String lu = texte(pdf.pdf("CGV-2026-04", Locale.FRENCH, "CMD-2026-0007", ACCEPTATION).orElseThrow());

        assertThat(lu).contains("Version CGV-2026-04, en vigueur depuis le 17/11/2026")
                .contains("Conditions acceptées le 18/11/2026 pour la commande CMD-2026-0007.");
        int article1 = lu.indexOf("Le vendeur est le garage identifié ci-dessous.");
        int identite = lu.indexOf("Numéro d'entreprise (BCE) : 0123.456.789");
        int article2 = lu.indexOf("Article 2. Champ d'application");
        assertThat(article1).isPositive();
        assertThat(identite).isGreaterThan(article1);
        assertThat(article2).isGreaterThan(identite);
        assertThat(lu).contains("Dénomination : Garage de démonstration")
                .contains("Nom commercial : Garage Démo")
                .contains("Adresse : Rue de l'Atelier 12, 1000 Bruxelles, Belgique")
                .contains("Courriel : garage@demo.test");
    }

    @Test
    @DisplayName("les mentions suivent la langue du texte servi, pas celle demandée")
    void langueDuTexteServi() throws IOException {
        when(versions.archive(TypeDocumentVersionne.CGV, "CGV-2026-04", Locale.of("nl")))
                .thenReturn(Optional.of(new TexteArchiveVue("CGV-2026-04", EFFET, "nl", TEXTE, true,
                        List.of("fr", "nl", "en"))));

        assertThat(texte(pdf.pdf("CGV-2026-04", Locale.of("nl"), "CMD-2026-0007", ACCEPTATION).orElseThrow()))
                .contains("Versie CGV-2026-04, van kracht sinds 17/11/2026")
                .contains("Voorwaarden aanvaard op 18/11/2026 voor bestelling CMD-2026-0007.")
                .contains("Benaming: Garage de démonstration")
                .contains("Handelsnaam: Garage Démo");
    }

    @Test
    @DisplayName("version inconnue : aucun document, jamais un texte approchant")
    void versionInconnue() {
        when(versions.archive(TypeDocumentVersionne.CGV, "CGV-1999-99", Locale.FRENCH))
                .thenReturn(Optional.empty());

        assertThat(pdf.pdf("CGV-1999-99", Locale.FRENCH, "CMD-2026-0007", ACCEPTATION)).isEmpty();
    }
}
