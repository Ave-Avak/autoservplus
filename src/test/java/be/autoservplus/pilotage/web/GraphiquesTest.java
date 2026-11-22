package be.autoservplus.pilotage.web;

import be.autoservplus.pilotage.service.dto.JourChiffre;
import be.autoservplus.pilotage.service.dto.MontantCategorie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Géométrie des graphiques du tableau de bord (A25)")
class GraphiquesTest {

    private static List<JourChiffre> trenteJours(String... derniers) {
        List<JourChiffre> serie = new ArrayList<>();
        LocalDate fin = LocalDate.of(2026, 11, 21);
        for (int i = 29; i >= 0; i--) {
            BigDecimal valeur = i < derniers.length
                    ? new BigDecimal(derniers[derniers.length - 1 - i]) : BigDecimal.ZERO;
            serie.add(new JourChiffre(fin.minusDays(i), valeur));
        }
        return serie;
    }

    @Test
    @DisplayName("l'échelle monte au premier nombre rond au-dessus du maximum")
    void echelleRonde() {
        assertThat(Graphiques.echelle(new BigDecimal("231.57"))).isEqualByComparingTo("500");
        assertThat(Graphiques.echelle(new BigDecimal("80.00"))).isEqualByComparingTo("100");
        assertThat(Graphiques.echelle(new BigDecimal("100.00"))).isEqualByComparingTo("100");
        assertThat(Graphiques.echelle(new BigDecimal("1500"))).isEqualByComparingTo("2000");
        assertThat(Graphiques.echelle(new BigDecimal("9.99"))).isEqualByComparingTo("10");
        assertThat(Graphiques.echelle(BigDecimal.ZERO)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("la courbe a un sommet par jour, du bord gauche au bord droit")
    void courbeComplete() {
        Graphiques.Courbe courbe = Graphiques.courbe(trenteJours("50.00", "100.00"));

        String[] sommets = courbe.points().split(" ");
        assertThat(sommets).hasSize(30);
        assertThat(sommets[0]).isEqualTo("64.0,212.0");
        assertThat(sommets[28]).as("50 sur une échelle de 100 : à mi-hauteur").endsWith(",112.0");
        assertThat(sommets[29]).as("le maximum touche le haut du repère").isEqualTo("624.0,12.0");
        assertThat(courbe.vide()).isFalse();
    }

    @Test
    @DisplayName("trois graduations, de zéro au sommet de l'échelle")
    void graduations() {
        Graphiques.Courbe courbe = Graphiques.courbe(trenteJours("231.57"));

        assertThat(courbe.ordonnees()).extracting(g -> g.valeur().stripTrailingZeros().toPlainString())
                .containsExactly("0", "250", "500");
    }

    @Test
    @DisplayName("une date tous les sept jours, la dernière étant aujourd'hui")
    void dates() {
        Graphiques.Courbe courbe = Graphiques.courbe(trenteJours());

        assertThat(courbe.abscisses()).extracting(Graphiques.Repere::jour)
                .containsExactly(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 7),
                        LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 21));
    }

    @Test
    @DisplayName("sans aucune vente, la courbe se dit vide")
    void courbeVide() {
        assertThat(Graphiques.courbe(trenteJours()).vide()).isTrue();
    }

    @Test
    @DisplayName("les coordonnées gardent le point décimal sur une machine en français")
    void pointDecimal() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertThat(Graphiques.courbe(trenteJours("10.00")).points()).doesNotContain(",0 ").contains(".0,");
        } finally {
            Locale.setDefault(machine);
        }
    }

    @Test
    @DisplayName("la plus forte catégorie prend toute la largeur, les autres en proportion")
    void barresProportionnelles() {
        Graphiques.Barres barres = Graphiques.barres(List.of(
                new MontantCategorie("Freinage", new BigDecimal("200.00")),
                new MontantCategorie("Filtration", new BigDecimal("50.00"))));

        assertThat(barres.barres()).extracting(Graphiques.Barre::largeur).containsExactly(640.0, 160.0);
        assertThat(barres.hauteur()).isEqualTo(88);
        assertThat(barres.barres().get(1).yBarre()).isGreaterThan(barres.barres().get(0).yBarre());
        assertThat(barres.vide()).isFalse();
    }

    @Test
    @DisplayName("sans catégorie, l'histogramme se dit vide")
    void barresVides() {
        assertThat(Graphiques.barres(List.of()).vide()).isTrue();
    }
}
