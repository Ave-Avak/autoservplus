package be.autoservplus.facturation.service;

import be.autoservplus.config.IdentiteGarage;
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
 * Contenu du PDF, lu dans le PDF lui-meme : chaque mention obligatoire d une
 * facture belge doit s y retrouver.
 *
 * <p>Le {@code MessageSource} est le vrai, charge des fichiers {@code i18n} livres :
 * un bouchon rendrait le test aveugle a une cle manquante, qui est precisement
 * l erreur la plus probable sur un document multilingue.</p>
 */
@DisplayName("GenerateurPdfFacture")
class GenerateurPdfFactureTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage(
            "AutoServ+ SRL", "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
            "0123.456.789", "BE0123456789", "BE68 5390 0754 7034",
            "+32 2 000 00 00", "facturation@autoservplus.be");

    private final GenerateurPdfFacture generateur =
            new GenerateurPdfFacture(messageSourceReel(), GARAGE);

    private static MessageSource messageSourceReel() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static DocumentFacture.ClientFacture marie() {
        return new DocumentFacture.ClientFacture("Marie Dupont", "Avenue Louise", "250",
                "1050", "Ixelles", "Belgique", "marie@exemple.be");
    }

    /** Facture a taux mixtes 6 % / 21 %, le cas ou la ventilation devient obligatoire. */
    private static DocumentFacture factureMultiTaux(Locale locale) {
        List<DocumentFacture.LigneFacture> lignes = List.of(
                new DocumentFacture.LigneFacture("Plaquettes de frein avant", 1,
                        new BigDecimal("100.00"), new BigDecimal("21.00"), new BigDecimal("100.00")),
                new DocumentFacture.LigneFacture("Recyclage huile usagee", 1,
                        new BigDecimal("50.00"), new BigDecimal("6.00"), new BigDecimal("50.00")));
        VentilationTva ventilation = new VentilationTva(List.of(
                new VentilationTva.TrancheTva(new BigDecimal("6"),
                        new BigDecimal("50.00"), new BigDecimal("3.00")),
                new VentilationTva.TrancheTva(new BigDecimal("21"),
                        new BigDecimal("100.00"), new BigDecimal("21.00"))));
        return new DocumentFacture("2026-0042", Instant.parse("2026-08-22T14:30:00Z"),
                "CMD-2026-0107", Instant.parse("2026-08-22T14:28:00Z"),
                marie(), lignes, ventilation,
                new BigDecimal("150.00"), new BigDecimal("24.00"), new BigDecimal("174.00"),
                locale);
    }

    /**
     * Texte du PDF, blancs normalises : une cellule etroite renvoie son libelle a la
     * ligne (« Maatstaf van / heffing »), ce qui n est pas un defaut du document mais
     * casserait des assertions litterales.
     */
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
    @DisplayName("porte le numero, la date d'emission et la commande d'origine")
    void identificationDuDocument() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.FRENCH)));

        assertThat(texte).contains("Facture", "2026-0042", "CMD-2026-0107");
        assertThat(texte).contains("22 août 2026");
    }

    @Test
    @DisplayName("porte l'identification legale complete du garage : BCE, TVA, siege, IBAN")
    void mentionsDuGarage() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.FRENCH)));

        assertThat(texte)
                .contains("AutoServ+ SRL")
                .contains("Rue de l'Atelier 12, 1000 Bruxelles")
                .contains("0123.456.789")
                .contains("BE0123456789")
                .contains("BE68 5390 0754 7034");
    }

    @Test
    @DisplayName("identifie le client et son adresse")
    void identificationDuClient() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.FRENCH)));

        assertThat(texte).contains("Marie Dupont", "Avenue Louise 250, 1050 Ixelles",
                "marie@exemple.be");
    }

    @Test
    @DisplayName("detaille chaque ligne : description, quantite, prix unitaire, taux")
    void detailDesLignes() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.FRENCH)));

        assertThat(texte).contains("Plaquettes de frein avant", "Recyclage huile usagee");
        assertThat(texte).contains("21 %", "6 %");
    }

    @Test
    @DisplayName("ventile la TVA par taux et affiche les trois totaux")
    void ventilationEtTotaux() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.FRENCH)));

        // Ventilation : base imposable et TVA de chaque taux, mention obligatoire
        // des qu une facture melange plusieurs taux.
        assertThat(texte).contains("Base imposable");
        assertThat(texte).contains("50,00", "3,00", "100,00", "21,00");
        // Totaux : HTVA, TVA, TVAC.
        assertThat(texte).contains("Total HTVA", "Total TVA", "Total TVAC");
        assertThat(texte).contains("150,00", "24,00", "174,00");
    }

    @Test
    @DisplayName("porte les conditions de paiement et la mention de conservation")
    void mentionsLegales() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.FRENCH)));

        assertThat(texte).contains("acquittée");
        assertThat(texte).contains("sept ans");
    }

    @Test
    @DisplayName("la facture est emise dans la langue du membre, en euros")
    void factureEnNeerlandais() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.forLanguageTag("nl"))));

        assertThat(texte).contains("Factuur", "Gefactureerd aan", "Maatstaf van heffing");
        assertThat(texte).doesNotContain("Base imposable");
    }

    @Test
    @DisplayName("en anglais aussi, les montants restent en euros et non en dollars")
    void deviseForceeEnEuros() throws Exception {
        String texte = texteDu(generateur.engendrer(factureMultiTaux(Locale.ENGLISH)));

        assertThat(texte).contains("Invoice", "Taxable base");
        assertThat(texte)
                .as("Une locale anglaise non contrainte imprimerait des dollars")
                .doesNotContain("$");
    }

    @Test
    @DisplayName("un membre sans adresse obtient quand meme sa facture")
    void clientSansAdresse() throws Exception {
        DocumentFacture sansAdresse = new DocumentFacture("2026-0043",
                Instant.parse("2026-08-22T14:30:00Z"), "CMD-2026-0108",
                Instant.parse("2026-08-22T14:28:00Z"),
                new DocumentFacture.ClientFacture("Jean Sansadresse", null, null, null, null,
                        "Belgique", "jean@exemple.be"),
                List.of(new DocumentFacture.LigneFacture("Ampoule H7", 2,
                        new BigDecimal("9.90"), new BigDecimal("21.00"), new BigDecimal("19.80"))),
                new VentilationTva(List.of(new VentilationTva.TrancheTva(
                        new BigDecimal("21"), new BigDecimal("19.80"), new BigDecimal("4.16")))),
                new BigDecimal("19.80"), new BigDecimal("4.16"), new BigDecimal("23.96"),
                Locale.FRENCH);

        String texte = texteDu(generateur.engendrer(sansAdresse));

        assertThat(texte).contains("Jean Sansadresse", "2026-0043");
    }
}
