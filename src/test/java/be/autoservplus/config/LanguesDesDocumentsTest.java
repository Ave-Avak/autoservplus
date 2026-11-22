package be.autoservplus.config;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.support.GaragesDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La langue d'un document suit la région du siège du garage, et non le client :
 * néerlandais en Flandre, français en Wallonie, l'une ou l'autre à Bruxelles. Le réglage
 * est lu comme au démarrage, par la liaison de la configuration.
 */
@DisplayName("Langues des documents du garage (GARAGE_LANGUES_DOCUMENTS)")
class LanguesDesDocumentsTest {

    private static IdentiteGarage lie(String valeur) {
        return new Binder(new MapConfigurationPropertySource(
                Map.of("autoservplus.garage.langues-documents", valeur)))
                .bind("autoservplus.garage", IdentiteGarage.class).get();
    }

    @Test
    @DisplayName("siège en Flandre : néerlandais pour tous, membre francophone compris")
    void siegeFlamand() {
        IdentiteGarage garage = GaragesDeTest.flamand();

        assertThat(garage.langueParDefaut()).isEqualTo(Langue.nl);
        assertThat(garage.langueDuDocument(Langue.fr)).isEqualTo(Langue.nl);
        assertThat(garage.langueDuDocument(Langue.en)).isEqualTo(Langue.nl);
    }

    @Test
    @DisplayName("siège à Bruxelles : la langue du membre si c'est le français ou le néerlandais, sinon le français")
    void siegeBruxellois() {
        IdentiteGarage garage = GaragesDeTest.bruxellois();

        assertThat(garage.langueDuDocument(Langue.nl)).isEqualTo(Langue.nl);
        assertThat(garage.langueDuDocument(Langue.fr)).isEqualTo(Langue.fr);
        assertThat(garage.langueDuDocument(Langue.en)).isEqualTo(Langue.fr);
    }

    @Test
    @DisplayName("siège en Wallonie : français pour tous")
    void siegeWallon() {
        assertThat(GaragesDeTest.wallon().langueDuDocument(Langue.nl)).isEqualTo(Langue.fr);
    }

    @Test
    @DisplayName("le réglage se lit dans l'ordre, la première langue étant celle par défaut")
    void lectureDuReglage() {
        assertThat(lie("fr,nl").languesDocuments()).containsExactly(Langue.fr, Langue.nl);
        assertThat(lie("nl,fr").langueParDefaut()).isEqualTo(Langue.nl);
        assertThat(lie("nl").languesDocuments()).containsExactly(Langue.nl);
    }

    @Test
    @DisplayName("sans réglage : le français")
    void sansReglage() {
        IdentiteGarage sansLangue = new IdentiteGarage("Garage", "Garage", null, "Rue", "1", "1000", "Bruxelles",
                "Belgique", "0123.456.789", null, "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00",
                "garage@exemple.be", null);

        assertThat(sansLangue.languesDocuments()).containsExactly(Langue.fr);
    }

    @Test
    @DisplayName("l'anglais, une langue inconnue ou une répétition font refuser le démarrage")
    void valeursRefusees() {
        assertThatThrownBy(() -> lie("en")).isInstanceOf(BindException.class)
                .hasRootCauseMessage("GARAGE_LANGUES_DOCUMENTS refusée : une ou deux langues parmi fr et nl, "
                        + "sans répétition, la première étant la langue par défaut. Reçu : [en]");
        assertThatThrownBy(() -> lie("fr,en")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> lie("de")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> lie("fr,fr")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> new IdentiteGarage("Garage", "Garage", null, "Rue", "1", "1000", "Bruxelles",
                "Belgique", "0123.456.789", null, "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00",
                "garage@exemple.be", List.of(Langue.en)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
