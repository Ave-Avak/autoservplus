package be.autoservplus.catalogue.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Motifs de la recherche du catalogue")
class MotifRechercheTest {

    @Test
    @DisplayName("un motif par mot, les places restantes acceptent tout")
    void unMotifParMot() {
        assertThat(MotifRecherche.mots("  Plaquettes   FREINAGE "))
                .containsExactly("%plaquettes%", "%freinage%", "%", "%", "%");
    }

    @Test
    @DisplayName("au-delà de cinq mots, les suivants sont ignorés, un mot répété ne compte qu'une fois")
    void cinqMotsAuPlus() {
        assertThat(MotifRecherche.mots("a a b c d e f"))
                .containsExactly("%a%", "%b%", "%c%", "%d%", "%e%");
    }

    @Test
    @DisplayName("les jokers de chaque mot restent neutralisés")
    void jokersNeutralisesParMot() {
        assertThat(MotifRecherche.mots("100% a_b"))
                .containsExactly("%100!%%", "%a!_b%", "%", "%", "%");
    }
}
