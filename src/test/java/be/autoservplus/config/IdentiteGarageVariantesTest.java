package be.autoservplus.config;

import be.autoservplus.identite.domain.Langue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Variantes néerlandaises de l'identité du garage")
class IdentiteGarageVariantesTest {

    private static final Locale NEERLANDAIS = Locale.of("nl");

    private static IdentiteGarage garage(List<Langue> langues, IdentiteGarage.MentionsNeerlandaises nl) {
        return new IdentiteGarage("Garage Démo", "Garage de démonstration", "SRL",
                "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique",
                "0123.456.789", "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034",
                "+32 2 000 00 00", "garage@demo.test", langues, nl);
    }

    private static final IdentiteGarage.MentionsNeerlandaises COMPLETES =
            new IdentiteGarage.MentionsNeerlandaises("BV", "Werkplaatsstraat", "Brussel", "België", "RPR Brussel");

    @Test
    @DisplayName("un document en néerlandais imprime les variantes saisies")
    void variantesEnNeerlandais() {
        IdentiteGarage enNeerlandais = garage(List.of(Langue.fr, Langue.nl), COMPLETES).pour(NEERLANDAIS);

        assertThat(enNeerlandais.denomination()).isEqualTo("Garage de démonstration BV");
        assertThat(enNeerlandais.adresseLisible()).isEqualTo("Werkplaatsstraat 12, 1000 Brussel");
        assertThat(enNeerlandais.pays()).isEqualTo("België");
        assertThat(enNeerlandais.rpm()).isEqualTo("RPR Brussel");
    }

    @Test
    @DisplayName("l'ordre des langues ne change rien : le néerlandais par défaut les imprime aussi")
    void ordreDesLanguesIndifferent() {
        IdentiteGarage enNeerlandais = garage(List.of(Langue.nl, Langue.fr), COMPLETES).pour(NEERLANDAIS);

        assertThat(enNeerlandais.rpm()).isEqualTo("RPR Brussel");
    }

    @Test
    @DisplayName("un document en français ou en anglais garde les valeurs de base")
    void autresLanguesInchangees() {
        IdentiteGarage identite = garage(List.of(Langue.fr, Langue.nl), COMPLETES);

        assertThat(identite.pour(Locale.FRENCH)).isSameAs(identite);
        assertThat(identite.pour(Locale.ENGLISH)).isSameAs(identite);
        assertThat(identite.pour(null)).isSameAs(identite);
    }

    @Test
    @DisplayName("une variante vide se replie sur la valeur de base, mention par mention")
    void repliMentionParMention() {
        var partielles = new IdentiteGarage.MentionsNeerlandaises(null, " ", null, null, "RPR Brussel");
        IdentiteGarage enNeerlandais = garage(List.of(Langue.fr, Langue.nl), partielles).pour(NEERLANDAIS);

        assertThat(enNeerlandais.rpm()).isEqualTo("RPR Brussel");
        assertThat(enNeerlandais.denomination()).isEqualTo("Garage de démonstration SRL");
        assertThat(enNeerlandais.adresseLisible()).isEqualTo("Rue de l'Atelier 12, 1000 Bruxelles");
        assertThat(enNeerlandais.pays()).isEqualTo("Belgique");
    }

    @Test
    @DisplayName("sans aucune variante, un siège flamand imprime ses valeurs de base")
    void siegeFlamandSansVariante() {
        IdentiteGarage identite = garage(List.of(Langue.nl), null);

        assertThat(identite.pour(NEERLANDAIS).rpm()).isEqualTo("RPM Bruxelles");
        assertThat(identite.nl()).isNotNull();
    }
}
