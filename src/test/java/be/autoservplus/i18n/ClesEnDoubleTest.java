package be.autoservplus.i18n;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucune clé en double dans les fichiers de traduction.
 *
 * <p>Une clé déclarée deux fois ne produit aucune erreur : la seconde valeur l'emporte en
 * silence. C'est ainsi que le titre du groupe « Paramètres » du menu du back-office a
 * affiché « Paramètres de réservation », la clé du lien de même nom ayant été ajoutée
 * plus bas sans que rien ne le signale.</p>
 */
class ClesEnDoubleTest {

    @ParameterizedTest
    @ValueSource(strings = {"messages.properties", "messages_nl.properties", "messages_en.properties"})
    void aucuneCleEnDouble(String fichier) throws IOException {
        List<String> lignes = Files.readAllLines(
                Path.of("src/main/resources/i18n", fichier), StandardCharsets.UTF_8);
        Set<String> vues = new HashSet<>();
        Set<String> doubles = new HashSet<>();
        boolean suite = false;
        for (String ligne : lignes) {
            String propre = ligne.strip();
            // Une ligne qui suit une barre oblique inverse finale continue la valeur
            // précédente : ce n'est pas une clé.
            boolean continuation = suite;
            suite = propre.endsWith("\\") && !propre.endsWith("\\\\");
            if (continuation || propre.isEmpty() || propre.startsWith("#") || !propre.contains("=")) {
                continue;
            }
            String cle = propre.substring(0, propre.indexOf('=')).strip();
            if (!vues.add(cle)) {
                doubles.add(cle);
            }
        }
        assertThat(doubles).as("clés déclarées deux fois dans " + fichier).isEmpty();
    }
}
