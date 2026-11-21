package be.autoservplus.config;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langue lue hors de toute requête, sur une machine en anglais.
 *
 * <p>Le fil du test peut porter le contexte d'une requête précédente. La lecture se fait
 * donc sur un fil neuf, comme une tâche planifiée : aucun contexte n'y existe, et seul le
 * repli décide.</p>
 */
@SpringBootTest
@DisplayName("Langue hors requête (intégration)")
class LangueHorsRequeteIT extends SocleIntegration {

    @Autowired private MessageSource messages;

    @Test
    @DisplayName("sur une machine en anglais, un texte rédigé hors requête sort en français")
    void francaisHorsRequete() throws Exception {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String[] resultat = CompletableFuture.supplyAsync(() -> {
                Locale langue = LocaleContextHolder.getLocale();
                return new String[]{langue.getLanguage(), messages.getMessage("connexion.envoyer", null, langue)};
            }, tache -> new Thread(tache).start()).get();

            assertThat(resultat[0]).as("langue hors requête").isEqualTo("fr");
            assertThat(resultat[1]).as("texte rédigé hors requête").isEqualTo("Se connecter");
        } finally {
            Locale.setDefault(machine);
        }
    }
}
