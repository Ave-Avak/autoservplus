package be.autoservplus.accessibilite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fous sur {@code css/autoservplus.css}.
 *
 * <p>{@code :invalid} colorait en rouge tout champ requis des l affichage, avant toute
 * saisie : un formulaire vierge se presentait comme une erreur. {@code :user-invalid}
 * n agit qu apres interaction ; le retour de {@code :invalid}, raccourci tentant, est
 * refuse ici.</p>
 */
@DisplayName("Feuille de style")
class FeuilleDeStyleTest {

    private static final Path FEUILLE = Path.of("src/main/resources/static/css/autoservplus.css");
    private static final Pattern INVALID_NU = Pattern.compile("(?<!user-):invalid\\b");

    @Test
    @DisplayName("aucun champ n'est marqué en faute avant interaction")
    void pasDeInvalidNu() throws Exception {
        String sansCommentaires = Files.readString(FEUILLE, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "");
        assertThat(INVALID_NU.matcher(sansCommentaires).find())
                .as(":invalid sans user- dans la feuille de style")
                .isFalse();
    }
}
