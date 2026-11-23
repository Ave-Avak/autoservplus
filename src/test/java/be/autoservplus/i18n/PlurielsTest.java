package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un nombre s'accorde par {@code choice}, jamais par un pluriel entre parenthèses :
 * « 1 commande(s) » se lisait sur les listes du back-office.
 *
 * <p>Le formulaire type de rétractation fait exception. Son texte reprend mot pour mot
 * l'annexe 2 du livre VI du Code de droit économique, parenthèses comprises.</p>
 */
@DisplayName("Pluriels des messages")
class PlurielsTest {

    private static final Pattern PLURIEL_ENTRE_PARENTHESES =
            Pattern.compile("\\((s|es|en|n|''s|'s)\\)");

    private static final String TEXTE_OFFICIEL = "retractation.formulaire.";

    private static Properties charger(String fichier) throws IOException {
        Properties messages = new Properties();
        try (InputStream flux = PlurielsTest.class.getResourceAsStream("/i18n/" + fichier)) {
            messages.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        }
        return messages;
    }

    @ParameterizedTest
    @ValueSource(strings = {"messages.properties", "messages_nl.properties", "messages_en.properties"})
    @DisplayName("aucun pluriel entre parenthèses hors du formulaire type de rétractation")
    void aucunPlurielEntreParentheses(String fichier) throws IOException {
        Properties messages = charger(fichier);
        List<String> fautives = messages.stringPropertyNames().stream()
                .filter(cle -> !cle.startsWith(TEXTE_OFFICIEL))
                .filter(cle -> PLURIEL_ENTRE_PARENTHESES.matcher(messages.getProperty(cle)).find())
                .sorted()
                .toList();
        assertThat(fautives).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"messages.properties", "messages_nl.properties", "messages_en.properties"})
    @DisplayName("la légende des commandes s'accorde à zéro, un et plusieurs")
    void legendeAccordee(String fichier) throws IOException {
        String modele = charger(fichier).getProperty("admin.ventes.commandes.legende");
        String aucune = new MessageFormat(modele, Locale.ROOT).format(new Object[]{0L});
        String une = new MessageFormat(modele, Locale.ROOT).format(new Object[]{1L});
        String deux = new MessageFormat(modele, Locale.ROOT).format(new Object[]{2L});
        assertThat(une).isNotEqualTo(deux.replace("2", "1"));
        // Le français accorde zéro au singulier, le néerlandais et l'anglais au pluriel.
        boolean francais = fichier.equals("messages.properties");
        assertThat(aucune.replace("0", "1").equals(une)).isEqualTo(francais);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2})
    @DisplayName("l'apostrophe du pluriel néerlandais des notes de crédit survit au formatage")
    void apostropheDansUnChoix(long nombre) throws IOException {
        String modele = charger("messages_nl.properties").getProperty("admin.ventes.avoirs.legende");
        String rendu = new MessageFormat(modele, Locale.ROOT).format(new Object[]{nombre});
        assertThat(rendu).contains(nombre == 1 ? "1 creditnota," : nombre + " creditnota's,");
    }
}
