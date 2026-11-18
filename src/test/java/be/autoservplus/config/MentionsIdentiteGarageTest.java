package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Identité du garage en lignes, pour le courriel et le PDF")
class MentionsIdentiteGarageTest {

    private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

    MentionsIdentiteGarageTest() {
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
    }

    private static IdentiteGarage garage(String nomCommercial) {
        return new IdentiteGarage(nomCommercial, "Garage de démonstration", "SRL",
                "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
                "0123.456.789", "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034",
                "+32 2 000 00 00", "garage@demo.test");
    }

    @Test
    @DisplayName("VI.45, § 1er, 2° : le nom commercial suit la dénomination et la forme juridique")
    void nomCommercialNomme() {
        assertThat(new MentionsIdentiteGarage(garage("Garage Démo"), messages).lignes(Locale.FRENCH))
                .startsWith("Dénomination : Garage de démonstration", "Forme juridique : SRL",
                        "Nom commercial : Garage Démo", "Adresse : Rue de l'Atelier 12, 1000 Bruxelles, Belgique");
    }

    @Test
    @DisplayName("sans enseigne configurée, aucune ligne : le garage se présente sous sa raison sociale")
    void sansNomCommercial() {
        assertThat(new MentionsIdentiteGarage(garage(" "), messages).lignes(Locale.FRENCH))
                .noneMatch(ligne -> ligne.startsWith("Nom commercial"))
                .hasSize(8);
    }
}
