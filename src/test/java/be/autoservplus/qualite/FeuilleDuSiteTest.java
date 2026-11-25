package be.autoservplus.qualite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La feuille du site, compilée au build depuis {@code src/main/scss}.
 *
 * <p>Lue sur le classpath, là où Maven l'a produite à {@code generate-resources} : le test
 * porte sur ce qui sera servi, pas sur les sources.</p>
 */
@DisplayName("Feuille de style compilée")
class FeuilleDuSiteTest {

    private static final Pattern ADRESSE = Pattern.compile("url\\(\"?([^\")]+)\"?\\)");

    private static String feuille() throws IOException {
        try (InputStream flux = FeuilleDuSiteTest.class.getResourceAsStream("/static/css/site.css")) {
            assertThat(flux).as("site.css absente : la phase generate-resources n'a pas tourné").isNotNull();
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("aucune image data: : les icônes de Bootstrap sont des fichiers servis par l'application")
    void aucuneImageEnLigne() throws IOException {
        assertThat(feuille()).doesNotContain("data:");
    }

    @Test
    @DisplayName("chaque fichier cité par la feuille existe sous static")
    void fichiersCites() throws IOException {
        Matcher adresses = ADRESSE.matcher(feuille());
        List<String> cites = adresses.results().map(r -> r.group(1)).distinct().toList();

        assertThat(cites).as("icônes et polices").hasSizeGreaterThanOrEqualTo(24);
        assertThat(cites).allSatisfy(adresse -> {
            // Adresse relative à la feuille, servie sous /css.
            String chemin = "/" + URI.create("static/css/").resolve(adresse).getPath();
            assertThat(FeuilleDuSiteTest.class.getResource(chemin))
                    .as("fichier cité %s", adresse).isNotNull();
        });
    }

    @Test
    @DisplayName("le thème de la charte est compilé : bleu marine primaire, Inter en tête de pile")
    void themeDeLaCharte() throws IOException {
        String css = feuille();

        assertThat(css).containsPattern("--bs-primary:\\s*#1F3864");
        assertThat(css).containsPattern("--bs-font-sans-serif:\\s*\"Inter\"");
    }
}
