package be.autoservplus.catalogue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Catégorie et rayon")
class CategorieTest {

    @Test
    @DisplayName("une catégorie de produits sans rayon donné va aux pièces auto, comme la migration")
    void rayonParDefaut() {
        assertThat(new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE).getRayon())
                .contains(Rayon.PIECES_AUTO);
        assertThat(new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE).getRayon()).isEmpty();
    }

    @Test
    @DisplayName("se range dans un autre rayon")
    void ranger() {
        Categorie interieur = new Categorie("A_INTERIEUR", "Intérieur", Rayon.ACCESSOIRES);
        assertThat(interieur.getType()).isEqualTo(TypeCategorie.PIECE);
        assertThat(interieur.getRayon()).contains(Rayon.ACCESSOIRES);

        interieur.ranger(Rayon.ENTRETIEN);

        assertThat(interieur.getRayon()).contains(Rayon.ENTRETIEN);
    }

    @Test
    @DisplayName("refuse un rayon à une catégorie de prestations")
    void prestationsSansRayon() {
        Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);

        assertThatThrownBy(() -> entretien.ranger(Rayon.ACCESSOIRES)).isInstanceOf(IllegalStateException.class);
        assertThat(entretien.getRayon()).isEmpty();
    }

    @Test
    @DisplayName("donne le préfixe d'adresse de chaque rayon")
    void prefixes() {
        assertThat(Rayon.PIECES_AUTO.prefixe()).isEqualTo("pieces");
        assertThat(Rayon.ACCESSOIRES.prefixe()).isEqualTo("accessoires");
        assertThat(Rayon.ENTRETIEN.prefixe()).isEqualTo("entretien");
    }
}
