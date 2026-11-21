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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * regles de {@code MessageFormat}. Ici, c est {@code MessageFormat} lui-meme qui
 * tranche, ce qui rend le test juste par construction.</p>
 *
 * <p>Seuls les messages porteurs d au moins un argument sont concernes : Spring ne
 * passe par {@code MessageFormat} que lorsqu il en recoit
 * ({@code alwaysUseMessageFormat} est a faux par defaut), de sorte qu une apostrophe
 * dans un message sans argument est parfaitement licite, et il y en a des
 * centaines.</p>
 */
@DisplayName("Messages i18n a arguments")
class MessagesParametresTest {

    /** Un argument cité, avec son type s'il en déclare un : {0}, {1,number}, {2,choice,...}. */
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d)\\s*(?:,\\s*(\\w+))?");

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

    /**
     * Indices que le message déclare {@code number} ou {@code choice}, à un endroit au
     * moins. {@code MessageFormat} refuse d'y formater autre chose qu'un nombre.
     */
    private static Set<Integer> indicesNumeriques(String message) {
        Set<Integer> indices = new TreeSet<>();
        Matcher trouve = ARGUMENT.matcher(message);
        while (trouve.find()) {
            if ("number".equals(trouve.group(2)) || "choice".equals(trouve.group(2))) {
                indices.add(Integer.parseInt(trouve.group(1)));
            }
        }
        return indices;
    }

    /**
     * Ce qui cloche dans le rendu d'un message, ou {@code null}. Les types se lisent dans
     * le texte du message et non dans {@code MessageFormat} : une apostrophe isolée lui
     * cache justement les arguments qu'elle neutralise.
     *
     * @param nombre valeur donnée à chaque argument numérique
     */
    private static String defautDuRendu(String modele, int nombre) {
        List<Integer> indices = indicesCites(modele);
        Set<Integer> numeriques = indicesNumeriques(modele);
        Object[] arguments = new Object[indices.stream().mapToInt(Integer::intValue).max().orElse(0) + 1];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = numeriques.contains(i) ? (Object) nombre : SENTINELLE + i;
        }
        String rendu;
        try {
            rendu = new MessageFormat(modele, Locale.ROOT).format(arguments);
        } catch (IllegalArgumentException refus) {
            return "avec " + nombre + ", refusé par MessageFormat : " + refus.getMessage();
        }
        for (int indice : indices) {
            String attendu = numeriques.contains(indice) ? String.valueOf(nombre) : SENTINELLE + indice;
            if (!rendu.contains(attendu)) {
                return "avec " + nombre + ", argument " + indice + " absent : " + rendu;
            }
        }
        // Un argument neutralisé reste écrit tel quel, accolades comprises. Une
        // apostrophe doublée là où il ne fallait pas ressort en double.
        if (rendu.contains("{") || rendu.contains("}") || rendu.contains("''")) {
            return "avec " + nombre + ", accolade ou apostrophe en trop : " + rendu;
        }
        return null;
    }

    @Nested
    @DisplayName("Tous les messages a arguments, dans les trois langues")
    class BalayageComplet {

        /**
         * Le message est formate pour de vrai, avec une sentinelle par indice cite.
         * Si une apostrophe isolee neutralise un {@code {0}}, la sentinelle manque et
         * le cas tombe en nommant la cle fautive.
         *
         * <p>Un argument déclaré {@code number} ou {@code choice} reçoit un nombre. Un
         * message à {@code choice} est formaté avec 0, 1 et 2, pour passer par chaque
         * forme du pluriel : une apostrophe fautive peut n'être que dans l'une d'elles.</p>
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
                if (indicesCites(modele).isEmpty()) {
                    continue;
                }
                int[] nombres = modele.contains(",choice") ? new int[]{0, 1, 2} : new int[]{1};
                for (int nombre : nombres) {
                    String defaut = defautDuRendu(modele, nombre);
                    if (defaut != null) {
                        fautives.put(cle, defaut);
                        break;
                    }
                }
            }

            assertThat(fautives)
                    .as("""
                        Messages dont un argument n'est pas substitue, ou dont le rendu garde \
                        une accolade ou une apostrophe en double. Cause quasi certaine : \
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
         * donc pas : l administrateur lisait « Dossier datelier {0} ouvert. ». Cas
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
