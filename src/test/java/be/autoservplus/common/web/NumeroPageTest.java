package be.autoservplus.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Numéro de page borné")
class NumeroPageTest {

    @Test
    @DisplayName("un numéro négatif devient la première page")
    void negatif() {
        assertThat(NumeroPage.borne(-1, 12)).isZero();
        assertThat(NumeroPage.borne(Integer.MIN_VALUE, 12)).isZero();
    }

    @Test
    @DisplayName("un numéro ordinaire est gardé")
    void ordinaire() {
        assertThat(NumeroPage.borne(0, 12)).isZero();
        assertThat(NumeroPage.borne(41, 12)).isEqualTo(41);
    }

    @Test
    @DisplayName("le décalage du plus grand numéro tient dans un entier, page suivante comprise")
    void plafonne() {
        for (int taille : new int[]{1, 2, 12, 50, 100}) {
            int page = NumeroPage.borne(Integer.MAX_VALUE, taille);
            assertThat((long) (page + 1) * taille).isLessThanOrEqualTo(Integer.MAX_VALUE);
        }
    }
}
