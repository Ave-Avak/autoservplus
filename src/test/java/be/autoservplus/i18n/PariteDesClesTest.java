package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mêmes noms de clés dans les trois fichiers de traduction.
 *
 * <p>Une clé absente en néerlandais ou en anglais ne produit aucune erreur : Spring
 * retombe sur {@code messages.properties} et la page s'affiche en français, sans que rien
 * ne le signale. Le compte des clés après chaque fusion le voyait, à condition d'y penser.
 * Une résolution de conflit « version de main » a déjà effacé un bloc de clés de cette
 * façon. Les fichiers sont lus comme Spring les lit, continuations et échappements
 * compris.</p>
 */
@DisplayName("Parité des clés de traduction")
class PariteDesClesTest {

    private static final Path DOSSIER = Path.of("src/main/resources/i18n");

    private static Set<String> cles(String fichier) throws IOException {
        Properties proprietes = new Properties();
        try (Reader lecteur = Files.newBufferedReader(DOSSIER.resolve(fichier), StandardCharsets.UTF_8)) {
            proprietes.load(lecteur);
        }
        return new TreeSet<>(proprietes.stringPropertyNames());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"messages_nl.properties", "messages_en.properties"})
    @DisplayName("mêmes noms de clés que le fichier français")
    void memesClesQueLeFrancais(String fichier) throws IOException {
        Set<String> francais = cles("messages.properties");
        Set<String> traduction = cles(fichier);

        Set<String> absentes = new TreeSet<>(francais);
        absentes.removeAll(traduction);
        Set<String> enTrop = new TreeSet<>(traduction);
        enTrop.removeAll(francais);

        assertThat(absentes).as("clés du français absentes de " + fichier).isEmpty();
        assertThat(enTrop).as("clés de " + fichier + " absentes du français").isEmpty();
        assertThat(francais).as("fichier français lu").isNotEmpty();
    }
}
