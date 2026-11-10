package be.autoservplus.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PosteAtelier")
class PosteAtelierTest {

    @Test
    @DisplayName("la modification nettoie le libellé et vide une description blanche")
    void modification() {
        PosteAtelier poste = new PosteAtelier("Pont 1");
        poste.setDescription("Ancienne");

        poste.modifier("  Pont élévateur  ", "   ", 3);

        assertThat(poste.getLibelle()).isEqualTo("Pont élévateur");
        assertThat(poste.getDescription()).isNull();
        assertThat(poste.getOrdre()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("refuse un libellé vide ou trop long, à la création comme à la modification")
    void libelleInvalide() {
        assertThatThrownBy(() -> new PosteAtelier("  ")).isInstanceOf(IllegalArgumentException.class);
        PosteAtelier poste = new PosteAtelier("Pont 1");
        assertThatThrownBy(() -> poste.modifier("x".repeat(PosteAtelier.LONGUEUR_LIBELLE + 1), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(poste.getLibelle()).isEqualTo("Pont 1");
    }

    @Test
    @DisplayName("refuse un ordre négatif sans rien modifier")
    void ordreNegatif() {
        PosteAtelier poste = new PosteAtelier("Pont 1");

        assertThatThrownBy(() -> poste.modifier("Pont 2", null, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(poste.getLibelle()).isEqualTo("Pont 1");
    }
}
