package be.autoservplus.qualite;

import be.autoservplus.identite.service.PolitiqueMotDePasse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le navigateur et le serveur appliquent la même longueur minimale. Un {@code minlength}
 * resté à 12 sur la page de réinitialisation laissait passer côté navigateur ce que
 * {@link PolitiqueMotDePasse} refusait ensuite, avec un message qui ne disait plus la
 * même règle.
 */
@DisplayName("Longueur minimale du mot de passe identique dans les gabarits")
class LongueurDuMotDePasseTest {

    private static final Path GABARITS = Path.of("src/main/resources/templates");

    @Test
    @DisplayName("chaque champ d'un nouveau mot de passe porte le minimum de PolitiqueMotDePasse, ou aucun")
    void minimumDeLaPolitique() throws IOException {
        List<String> ecarts = new ArrayList<>();
        int champs = 0;
        try (Stream<Path> fichiers = Files.walk(GABARITS)) {
            for (Path fichier : fichiers.filter(f -> f.toString().endsWith(".html")).toList()) {
                String source = Files.readString(fichier, StandardCharsets.UTF_8);
                for (Element champ : Jsoup.parse(source).select("input[type=password][autocomplete=new-password]")) {
                    champs++;
                    String minimum = champ.attr("minlength");
                    if (!minimum.isEmpty() && !minimum.equals(String.valueOf(PolitiqueMotDePasse.MINIMUM_CARACTERES))) {
                        ecarts.add(GABARITS.relativize(fichier) + " #" + champ.id() + " minlength=" + minimum);
                    }
                }
            }
        }
        assertThat(champs).as("champs de nouveau mot de passe trouvés").isPositive();
        assertThat(ecarts).isEmpty();
    }
}
