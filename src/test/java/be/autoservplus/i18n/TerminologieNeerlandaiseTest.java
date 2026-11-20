package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un même document porte un seul nom en néerlandais. Les CGV publiées s'intitulent
 * « algemene verkoopvoorwaarden » : un libellé qui écrit « verkoopsvoorwaarden » renvoie
 * en apparence à un autre texte que celui que le membre accepte.
 */
@DisplayName("Terminologie néerlandaise")
class TerminologieNeerlandaiseTest {

    private static final Path MESSAGES_NL = Path.of("src", "main", "resources", "i18n", "messages_nl.properties");

    @Test
    @DisplayName("les CGV s'appellent « verkoopvoorwaarden », jamais « verkoopsvoorwaarden »")
    void verkoopvoorwaarden() throws Exception {
        List<String> lignes = Files.readAllLines(MESSAGES_NL, StandardCharsets.UTF_8);

        assertThat(lignes)
                .filteredOn(ligne -> ligne.toLowerCase(Locale.ROOT).contains("verkoopsvoorwaarden"))
                .isEmpty();
        assertThat(lignes).anyMatch(ligne -> ligne.contains("verkoopvoorwaarden"));
    }
}
