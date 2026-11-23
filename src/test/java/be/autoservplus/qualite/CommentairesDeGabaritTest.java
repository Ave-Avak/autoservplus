package be.autoservplus.qualite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un commentaire de gabarit s'écrit {@code <!--/* ... *}{@code /-->} : Thymeleaf le retire au
 * rendu. Un commentaire HTML ordinaire part dans la page et se lit par « afficher la
 * source », avec les raisons de conception et les numéros de règles qu'il cite.
 */
@DisplayName("Commentaires des gabarits")
class CommentairesDeGabaritTest {

    private static final Path GABARITS = Path.of("src/main/resources/templates");

    private static final Pattern COMMENTAIRE_ENVOYE = Pattern.compile("<!--(?!/\\*)");

    @Test
    @DisplayName("aucun commentaire de développeur n'est envoyé au navigateur")
    void aucunCommentaireEnvoye() throws IOException {
        List<String> fautifs;
        try (Stream<Path> fichiers = Files.walk(GABARITS)) {
            fautifs = fichiers.filter(chemin -> chemin.toString().endsWith(".html"))
                    .filter(CommentairesDeGabaritTest::porteUnCommentaireEnvoye)
                    .map(chemin -> GABARITS.relativize(chemin).toString())
                    .sorted()
                    .toList();
        }
        assertThat(fautifs).isEmpty();
    }

    private static boolean porteUnCommentaireEnvoye(Path gabarit) {
        try {
            return COMMENTAIRE_ENVOYE.matcher(Files.readString(gabarit, StandardCharsets.UTF_8)).find();
        } catch (IOException lectureImpossible) {
            throw new IllegalStateException(lectureImpossible);
        }
    }
}
