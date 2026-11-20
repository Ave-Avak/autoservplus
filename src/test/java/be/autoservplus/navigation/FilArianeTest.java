package be.autoservplus.navigation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cohérence de la table du fil d'Ariane, sans Spring : chaque chaîne remonte à une
 * racine en trois niveaux au plus (Charte graphique §6.1), et chaque libellé existe dans
 * les trois langues, avec une valeur seulement quand l'écran en fournit une.
 */
@DisplayName("Table du fil d'Ariane")
class FilArianeTest {

    private static final Path I18N = Path.of("src/main/resources/i18n");
    private static final Pattern VARIABLE = Pattern.compile("\\{([^}]+)}");

    private final TableFilAriane table = new TableFilAriane();

    @Test
    @DisplayName("chaque écran remonte à une racine en trois niveaux au plus")
    void troisNiveauxAuPlus() {
        for (TableFilAriane.Entree entree : table.entrees()) {
            List<TableFilAriane.Entree> chemin = table.chemin(entree);
            assertThat(TableFilAriane.RACINES).as("racine de %s", entree.patron())
                    .containsKey(chemin.get(0).parent());
            assertThat(chemin).as("niveaux sous la racine pour %s", entree.patron()).hasSizeLessThanOrEqualTo(3);
        }
    }

    @Test
    @DisplayName("un parent est une racine ou un écran de la table")
    void parentConnu() {
        for (TableFilAriane.Entree entree : table.entrees()) {
            assertThat(TableFilAriane.RACINES.containsKey(entree.parent()) || table.entree(entree.parent()).isPresent())
                    .as("parent %s de %s", entree.parent(), entree.patron())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("un parent ne demande aucune variable que la page n'a pas")
    void variablesDuParent() {
        for (TableFilAriane.Entree entree : table.entrees()) {
            assertThat(variables(entree.patron())).as("variables de %s pour son parent", entree.patron())
                    .containsAll(variables(entree.parent()));
        }
    }

    @Test
    @DisplayName("aucun écran n'est à la fois dans la table et sans fil")
    void exclusionsDisjointes() {
        Set<String> inscrits = table.entrees().stream().map(TableFilAriane.Entree::patron).collect(Collectors.toSet());
        assertThat(inscrits).doesNotContainAnyElementsOf(TableFilAriane.SANS_FIL.keySet());
    }

    @Test
    @DisplayName("chaque libellé existe en FR, NL et EN, avec {0} si et seulement si l'écran fournit une valeur")
    void libellesTraduits() throws IOException {
        List<String> defauts = new ArrayList<>();
        for (String fichier : List.of("messages.properties", "messages_nl.properties", "messages_en.properties")) {
            Properties messages = charger(fichier);
            for (String cle : TableFilAriane.RACINES.values()) {
                if (!messages.containsKey(cle)) {
                    defauts.add(fichier + " : clé absente " + cle);
                }
            }
            if (!messages.containsKey("fil-ariane.detail")) {
                defauts.add(fichier + " : clé absente fil-ariane.detail");
            }
            for (TableFilAriane.Entree entree : table.entrees()) {
                String texte = messages.getProperty(entree.cle());
                if (texte == null) {
                    defauts.add(fichier + " : clé absente " + entree.cle());
                } else if (texte.contains("{0}") != entree.dynamique()) {
                    defauts.add(fichier + " : " + entree.patron() + " " + entree.cle()
                            + (entree.dynamique() ? " sans {0}" : " attend une valeur que l'écran ne fournit pas"));
                }
            }
        }
        assertThat(defauts).isEmpty();
    }

    private static Set<String> variables(String patron) {
        Matcher m = VARIABLE.matcher(patron);
        Set<String> noms = new java.util.HashSet<>();
        while (m.find()) {
            noms.add(m.group(1));
        }
        return noms;
    }

    private static Properties charger(String fichier) throws IOException {
        Properties p = new Properties();
        try (Reader lecteur = Files.newBufferedReader(I18N.resolve(fichier), StandardCharsets.UTF_8)) {
            p.load(lecteur);
        }
        return p;
    }
}
