package be.autoservplus.qualite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Règles des sources Sass du site (Q-I-9) : elles portent sur nos fichiers seuls, Bootstrap
 * est compilé tel quel. Aucun {@code !important}, aucune requête {@code max-width} (mobile
 * d'abord), des {@code rem} partout sauf pour l'épaisseur d'une bordure ou d'un contour.
 */
@DisplayName("Sources Sass du site")
class SourcesDuSiteTest {

    private static final Path SOURCES = Path.of("src/main/scss");
    private static final Pattern COMMENTAIRE = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern DECLARATION_EN_PX = Pattern.compile("([a-z-]+)\\s*:[^;{}]*\\b\\d+(?:\\.\\d+)?px\\b");

    private static List<String> lignes() throws IOException {
        List<String> lignes = new ArrayList<>();
        try (Stream<Path> fichiers = Files.list(SOURCES)) {
            for (Path fichier : fichiers.filter(f -> f.toString().endsWith(".scss")).sorted().toList()) {
                String texte = COMMENTAIRE.matcher(Files.readString(fichier, StandardCharsets.UTF_8)).replaceAll("");
                texte.lines().forEach(ligne -> lignes.add(fichier.getFileName() + " : " + ligne.strip()));
            }
        }
        assertThat(lignes).as("sources lues").isNotEmpty();
        return lignes;
    }

    @Test
    @DisplayName("aucun !important")
    void aucunImportant() throws IOException {
        assertThat(lignes()).noneMatch(ligne -> ligne.contains("!important"));
    }

    @Test
    @DisplayName("mobile d'abord : aucune requête max-width ni media-breakpoint-down")
    void mobileDAbord() throws IOException {
        assertThat(lignes()).noneMatch(ligne -> ligne.contains("max-width") && ligne.contains("@media")
                || ligne.contains("media-breakpoint-down") || ligne.contains("media-breakpoint-only"));
    }

    @Test
    @DisplayName("pixels pour les bordures et les contours seulement")
    void pixelsPourLesBorduresSeules() throws IOException {
        List<String> fautives = new ArrayList<>();
        for (String ligne : lignes()) {
            // Le nom du fichier précède la ligne : « _gabarit.scss : ... ».
            Matcher declaration = DECLARATION_EN_PX.matcher(ligne.substring(ligne.indexOf(" : ") + 3));
            while (declaration.find()) {
                String propriete = declaration.group(1);
                if (!propriete.startsWith("border") && !propriete.startsWith("outline")) {
                    fautives.add(ligne);
                }
            }
        }
        assertThat(fautives).isEmpty();
    }
}
