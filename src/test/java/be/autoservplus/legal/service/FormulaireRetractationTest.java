package be.autoservplus.legal.service;

import be.autoservplus.config.IdentiteGarage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Formulaire de rétractation (annexe 2 du livre VI)")
class FormulaireRetractationTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage(
            "Garage Démo", "Garage de démonstration", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
            "0123.456.789", "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034",
            "+32 2 000 00 00", "garage@demo.test");

    /**
     * Marques d'édition des deux sources : renvois Justel « [1 ... ]1 », fin de citation
     * « ." .]1 », bornes d'amendement EUR-Lex (U+25BA, U+25C4), renvoi numéroté « (*1) »
     * et mentions entre crochets que l'entreprise devait compléter.
     */
    static final Pattern MARQUES = Pattern.compile(
            "\\[\\d|\\]\\d|\\u25BA|\\u25C4|\\(\\*\\d\\)|\\[l'entreprise|\\[hier moet|\\[here the");

    private final FormulaireRetractation formulaire = new FormulaireRetractation(GARAGE, messages());

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private static List<String> lignes(FormulaireRetractation.Formulaire f) {
        List<String> lignes = new ArrayList<>(List.of(f.titre(), f.entete(), f.destinataire()));
        lignes.addAll(f.rubriques());
        lignes.add(f.note());
        return lignes;
    }

    @Test
    @DisplayName("français : le texte de l'annexe 2, prérempli du nom, de l'adresse et du courriel du garage")
    void francais() {
        var f = formulaire.formulaire(Locale.FRENCH);

        assertThat(f.entete()).isEqualTo("(Veuillez compléter et renvoyer le présent formulaire "
                + "uniquement si vous souhaitez vous rétracter du contrat)");
        assertThat(f.destinataire()).isEqualTo("A l'attention de Garage de démonstration SRL, "
                + "Rue de l'Atelier 12, 1000 Bruxelles, Belgique, garage@demo.test:");
        assertThat(f.rubriques()).containsExactly(
                "Je/Nous (*) vous notifie/notifions (*) par la présente ma/notre (*) rétractation "
                        + "du contrat portant sur la vente du bien (*)/pour la prestation de service (*) ci-dessous",
                "Commandé le (*)/reçu le (*)",
                "Nom du (des) consommateur(s)",
                "Adresse du (des) consommateur(s)",
                "Signature du (des) consommateur(s) (uniquement en cas de notification du présent "
                        + "formulaire sur papier)",
                "Date");
        assertThat(f.note()).isEqualTo("(*) Biffez la mention inutile.");
    }

    @Test
    @DisplayName("néerlandais et anglais : les textes officiels, préremplis de la même identité")
    void neerlandaisEtAnglais() {
        assertThat(formulaire.formulaire(Locale.of("nl")).destinataire()).isEqualTo(
                "Aan Garage de démonstration SRL, Rue de l'Atelier 12, 1000 Bruxelles, Belgique, "
                        + "garage@demo.test:");
        assertThat(formulaire.formulaire(Locale.of("nl")).note())
                .isEqualTo("(*) Doorhalen wat niet van toepassing is.");
        var anglais = formulaire.formulaire(Locale.ENGLISH);
        assertThat(anglais.destinataire()).startsWith("To Garage de démonstration SRL, ");
        assertThat(anglais.rubriques().get(0)).isEqualTo("I/We (*) hereby give notice that I/We (*) "
                + "withdraw from my/our (*) contract of sale of the following goods (*)/for the "
                + "provision of the following service (*),");
        assertThat(anglais.note()).isEqualTo("(*) Delete as appropriate.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "nl", "en"})
    @DisplayName("aucune marque d'édition de Justel ni d'EUR-Lex n'atteint le formulaire")
    void sansMarquesDEdition(String langue) throws IOException {
        var f = formulaire.formulaire(Locale.of(langue));

        assertThat(lignes(f)).allSatisfy(ligne -> assertThat(MARQUES.matcher(ligne).find())
                .as(ligne).isFalse());
        String pdf = texteDu(formulaire.pdf(Locale.of(langue)));
        assertThat(MARQUES.matcher(pdf).find()).as(pdf).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "nl", "en"})
    @DisplayName("l'introduction de la page cite l'écran des commandes avec les guillemets de la langue")
    void introductionAvecLesGuillemetsDeLaLangue(String langue) {
        // Guillemets français en FR, guillemets anglais doubles en NL et en EN, comme
        // l'article 7 des CGV qui renvoie au même écran.
        ResourceBundleMessageSource messages = messages();
        Locale locale = Locale.of(langue);
        String ecran = messages.getMessage("commandes.nav", null, locale);
        String citation = langue.equals("fr") ? "« " + ecran + " »" : "“" + ecran + "”";
        assertThat(messages.getMessage("retractation.formulaire.usage", null, locale))
                .contains(citation)
                .doesNotContain(langue.equals("fr") ? "“" : "«");
    }

    @Test
    @DisplayName("le PDF porte les mêmes lignes que la page, dans le même ordre")
    void pdfIdentiqueALaPage() throws IOException {
        var f = formulaire.formulaire(Locale.FRENCH);

        String pdf = texteDu(formulaire.pdf(Locale.FRENCH));

        int position = -1;
        for (String ligne : lignes(f)) {
            int trouvee = pdf.indexOf(ligne.replaceAll("\\s+", " "));
            assertThat(trouvee).as(ligne).isGreaterThan(position);
            position = trouvee;
        }
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
}
