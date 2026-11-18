package be.autoservplus.legal.service;

import be.autoservplus.legal.domain.TypeDocumentVersionne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Texte de CGV-2026-04 tel que les fichiers i18n le portent, dans les trois langues.
 *
 * <p>L'article 7 reprend mot pour mot l'annexe 1 du livre VI CDE (FR, NL) et l'annexe I,
 * A, de la directive 2011/83/UE (EN). Ces sources portent des marques d'édition qui ne
 * doivent pas passer dans le texte présenté au consommateur.</p>
 */
@DisplayName("Conditions générales de vente CGV-2026-04")
class ConditionsGeneralesTest {

    /**
     * Marques propres aux sources de l'article 7, en plus de celles du formulaire : note
     * d'erratum Justel, borne d'amendement EUR-Lex (U+25BC), renvois numérotés des
     * instructions de l'annexe, « (1) » à « (6) » et « [2] », et mentions à compléter.
     */
    private static final Pattern MARQUES_ANNEXE = Pattern.compile(
            "Erratum|\\u25BC|\\(\\d\\)|\\[\\d\\]|\\[ins|\\[naam|\\[internetadres|\\[doorhalen"
                    + "|\\[delete|\\.\\.\\. \\[|\\u2026");

    private static final ResourceBundleMessageSource MESSAGES = messages();

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private static String texte(String cle, Locale langue) {
        return MESSAGES.getMessage(cle, null, langue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "nl", "en"})
    @DisplayName("aucune marque d'édition des sources dans les clauses des CGV")
    void aucuneMarqueDEdition(String langue) {
        for (String cle : TypeDocumentVersionne.CGV.cles()) {
            String clause = texte(cle, Locale.of(langue));
            assertThat(FormulaireRetractationTest.MARQUES.matcher(clause).find())
                    .as("marque d'édition dans %s (%s) : %s", cle, langue, clause).isFalse();
            assertThat(MARQUES_ANNEXE.matcher(clause).find())
                    .as("marque d'édition dans %s (%s) : %s", cle, langue, clause).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "nl", "en"})
    @DisplayName("plus aucun renvoi à la plateforme RLL, le Service de médiation reste")
    void plateformeRllRetiree(String langue) {
        // Plateforme européenne de règlement en ligne des litiges fermée le 20 juillet 2025
        // (règlement 2024/3228).
        for (String cle : TypeDocumentVersionne.CGV.cles()) {
            assertThat(texte(cle, Locale.of(langue))).doesNotContain("consumers/odr");
        }
        assertThat(texte("legal.cgv.art13.completer", Locale.of(langue)))
                .contains(langue.equals("nl") ? "consumentenombudsdienst.be" : "mediationconsommateur.be");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "nl", "en"})
    @DisplayName("la fonction en ligne est désignée par les libellés de l'interface")
    void fonctionEnLigneNommeeCommeALEcran(String langue) {
        // L'article 7 situe la fonction de rétractation en ligne (art. VI.61/2 CDE) par les
        // libellés de l'interface : un libellé renommé sans nouvelle version des CGV
        // enverrait le consommateur vers un lien qui n'existe plus.
        Locale locale = Locale.of(langue);
        assertThat(texte("legal.cgv.art7.en-ligne", locale))
                .contains(texte("commandes.nav", locale))
                .contains(texte("commandes.annulation.demander", locale));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "nl", "en"})
    @DisplayName("guillemets de la langue dans l'article 7")
    void guillemetsDeLaLangue(String langue) {
        // Guillemets français en FR, guillemets anglais doubles en NL et en EN, pour les
        // libellés de l'interface et la phrase d'entrée de l'article 7.
        Locale locale = Locale.of(langue);
        String ouvrant = langue.equals("fr") ? "« " : "“";
        assertThat(texte("legal.cgv.art7.entree", locale)).contains(ouvrant);
        assertThat(texte("legal.cgv.art7.en-ligne", locale)).contains(ouvrant);
        String autre = langue.equals("fr") ? "“" : "«";
        assertThat(texte("legal.cgv.art7.entree", locale) + texte("legal.cgv.art7.en-ligne", locale))
                .doesNotContain(autre);
    }
}
