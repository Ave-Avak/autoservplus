package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrouille les messages a arguments contre le piege d apostrophe de
 * {@link MessageFormat}.
 *
 * <p>Dans un message qui recoit des arguments, l apostrophe n est pas un caractere
 * ordinaire : elle <b>ouvre une zone litterale</b> qui court jusqu a l apostrophe
 * suivante ou jusqu a la fin. Une apostrophe isolee a donc deux effets, tous deux
 * silencieux : elle disparait de l affichage, et elle neutralise les
 * {@code {0}} qui la suivent, qui s affichent tels quels au lieu d etre remplaces.
 * Seul {@code ''} produit une apostrophe. Le francais etant plein d apostrophes, la
 * faute est facile et ne se voit qu a l ecran.</p>
 *
 * <p><b>Le controle est comportemental, pas syntaxique.</b> Chaque message est
 * reellement formate, puis on verifie que l argument fourni figure dans le
 * resultat. Une regle ecrite a la main sur le texte finirait par diverger des
 * regles de {@code MessageFormat} ; ici, c est {@code MessageFormat} lui-meme qui
 * tranche, ce qui rend le test juste par construction.</p>
 *
 * <p>Seuls les messages porteurs d au moins un argument sont concernes : Spring ne
 * passe par {@code MessageFormat} que lorsqu il en recoit
 * ({@code alwaysUseMessageFormat} est a faux par defaut), de sorte qu une apostrophe
 * dans un message sans argument est parfaitement licite — et il y en a des
 * centaines.</p>
 */
@DisplayName("Messages i18n a arguments")
class MessagesParametresTest {

    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d)}");

    /** Sentinelle improbable dans un libelle, pour que sa presence soit concluante. */
    private static final String SENTINELLE = "ZZQX";

    private static Properties charger(String fichier) {
        Properties messages = new Properties();
        try (InputStream flux = MessagesParametresTest.class
                .getResourceAsStream("/i18n/" + fichier)) {
            assertThat(flux).as("fichier %s introuvable au classpath", fichier).isNotNull();
            messages.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        } catch (IOException lectureImpossible) {
            throw new IllegalStateException(lectureImpossible);
        }
        return messages;
    }

    /** Indices d arguments cites par le message, dans l ordre. */
    private static List<Integer> indicesCites(String message) {
        List<Integer> indices = new ArrayList<>();
        Matcher trouve = ARGUMENT.matcher(message);
        while (trouve.find()) {
            indices.add(Integer.parseInt(trouve.group(1)));
        }
        return indices;
    }

    @Nested
    @DisplayName("Tous les messages a arguments, dans les trois langues")
    class BalayageComplet {

        /**
         * Le message est formate pour de vrai, avec une sentinelle par indice cite.
         * Si une apostrophe isolee neutralise un {@code {0}}, la sentinelle manque et
         * le cas tombe en nommant la cle fautive.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"messages.properties", "messages_nl.properties",
                "messages_en.properties"})
        @DisplayName("chaque argument declare est reellement substitue")
        void argumentsSubstitues(String fichier) {
            Properties messages = charger(fichier);
            Map<String, String> fautives = new TreeMap<>();

            for (String cle : messages.stringPropertyNames()) {
                String modele = messages.getProperty(cle);
                List<Integer> indices = indicesCites(modele);
                if (indices.isEmpty()) {
                    continue;
                }
                Object[] arguments = new Object[indices.stream().mapToInt(Integer::intValue)
                        .max().orElse(0) + 1];
                for (int i = 0; i < arguments.length; i++) {
                    arguments[i] = SENTINELLE + i;
                }
                String rendu = new MessageFormat(modele, Locale.ROOT).format(arguments);
                for (int indice : indices) {
                    if (!rendu.contains(SENTINELLE + indice)) {
                        fautives.put(cle, rendu);
                        break;
                    }
                }
            }

            assertThat(fautives)
                    .as("""
                        Messages dont un argument n'est pas substitue. Cause quasi certaine : \
                        une apostrophe isolee, qui ouvre une zone litterale dans MessageFormat. \
                        Correctif : la doubler ('' au lieu de ').""")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Ecran de planification, le cas qui a revele le defaut")
    class PlanificationF12 {

        /**
         * Les deux messages de l ecran d ouverture d un dossier d atelier (F12)
         * portaient une apostrophe non doublee. Le numero du dossier ne s affichait
         * donc pas — l administrateur lisait « Dossier datelier {0} ouvert. ». Cas
         * concret conserve a cote du balayage : il montre a quoi ressemblait la panne,
         * la ou le balayage se contente de l interdire.
         */
        @Test
        @DisplayName("le numero du dossier apparait, et l apostrophe survit")
        void numeroEtApostropheAffiches() {
            Properties messages = charger("messages.properties");

            String creee = new MessageFormat(
                    messages.getProperty("admin.planification.creee"), Locale.FRENCH)
                    .format(new Object[]{"ITV-2026-0011"});
            String deja = new MessageFormat(
                    messages.getProperty("admin.planification.deja"), Locale.FRENCH)
                    .format(new Object[]{"ITV-2026-0011"});

            assertThat(creee).contains("ITV-2026-0011").contains("d'atelier");
            assertThat(deja).contains("ITV-2026-0011").contains("d'atelier");
        }
    }
}
