package be.autoservplus.common.montant;

import be.autoservplus.common.montant.CalculTva.MontantTaxe;
import be.autoservplus.common.montant.CalculTva.Tranche;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Calcul de la TVA")
class CalculTvaTest {

    private static final BigDecimal VINGT_ET_UN = new BigDecimal("21.00");
    private static final BigDecimal SIX = new BigDecimal("6.00");

    /** Prix hors TVA dont plusieurs perdent ou gagnent un demi-cent une fois taxés. */
    private static final List<String> PRIX = List.of("0.01", "0.99", "9.95", "12.40", "18.90",
            "19.99", "46.50", "89.00", "118.00", "249.99", "1234.56");

    private static MontantTaxe ligne(String unitaireHtva, BigDecimal taux, int quantite) {
        return new MontantTaxe(taux, CalculTva.totalTvac(new BigDecimal(unitaireHtva), taux, quantite));
    }

    @ParameterizedTest(name = "taux de {0} %")
    @ValueSource(strings = {"6", "12", "21"})
    @DisplayName("le total d'une ligne est le prix unitaire affiché fois la quantité, et base plus TVA font le total")
    void identites(String taux) {
        BigDecimal tauxTva = new BigDecimal(taux);
        for (String prix : PRIX) {
            BigDecimal unitaireTvac = CalculTva.unitaireTvac(new BigDecimal(prix), tauxTva);
            assertThat(unitaireTvac.scale()).as(prix).isEqualTo(2);
            for (int quantite = 1; quantite <= 10; quantite++) {
                BigDecimal total = CalculTva.totalTvac(new BigDecimal(prix), tauxTva, quantite);
                assertThat(total).as("%s x %d", prix, quantite)
                        .isEqualByComparingTo(unitaireTvac.multiply(BigDecimal.valueOf(quantite)));

                Tranche tranche = CalculTva.ventiler(List.of(new MontantTaxe(tauxTva, total))).get(0);
                assertThat(tranche.base().add(tranche.tva())).as("%s x %d", prix, quantite)
                        .isEqualByComparingTo(total);
                assertThat(tranche.tvac()).isEqualByComparingTo(total);
                assertThat(tranche.base().scale()).isEqualTo(2);
                // La TVA retenue est celle du taux, au cent près : jamais plus d'un demi-cent
                // d'écart avec le taux appliqué à la base.
                BigDecimal tvaDuTaux = tranche.base().multiply(tauxTva).divide(new BigDecimal("100"));
                assertThat(tranche.tva().subtract(tvaDuTaux).abs())
                        .as("%s x %d", prix, quantite)
                        .isLessThanOrEqualTo(new BigDecimal("0.01"));
            }
        }
    }

    @Test
    @DisplayName("deux filtres à huile à 15,00 : 30,00 payés, base 24,79, TVA 5,21")
    void deuxFiltresAHuile() {
        assertThat(CalculTva.unitaireTvac(new BigDecimal("12.40"), VINGT_ET_UN)).isEqualByComparingTo("15.00");

        Tranche tranche = CalculTva.ventiler(List.of(ligne("12.40", VINGT_ET_UN, 2))).get(0);

        assertThat(tranche.tvac()).isEqualByComparingTo("30.00");
        assertThat(tranche.base()).isEqualByComparingTo("24.79");
        assertThat(tranche.tva()).isEqualByComparingTo("5.21");
    }

    @Test
    @DisplayName("deux jeux de plaquettes à 56,27 : 112,54 payés, base 93,01, TVA 19,53")
    void deuxJeuxDePlaquettes() {
        Tranche tranche = CalculTva.ventiler(List.of(ligne("46.50", VINGT_ET_UN, 2))).get(0);

        assertThat(tranche.tvac()).isEqualByComparingTo("112.54");
        assertThat(tranche.base()).isEqualByComparingTo("93.01");
        assertThat(tranche.tva()).isEqualByComparingTo("19.53");
    }

    @Test
    @DisplayName("six filtres à air à 22,87 : 137,22 payés, base 113,40, TVA 23,82")
    void sixFiltresAAir() {
        Tranche tranche = CalculTva.ventiler(List.of(ligne("18.90", VINGT_ET_UN, 6))).get(0);

        assertThat(tranche.tvac()).isEqualByComparingTo("137.22");
        assertThat(tranche.base()).isEqualByComparingTo("113.40");
        assertThat(tranche.tva()).isEqualByComparingTo("23.82");
    }

    @Test
    @DisplayName("deux lignes au même taux : la base se calcule sur leur somme, pas ligne par ligne")
    void baseSurLaSomme() {
        // Chaque filtre seul aurait une base de 12,40, soit 24,80 à deux. Sur la somme de
        // 30,00, la base est de 24,79.
        assertThat(CalculTva.base(new BigDecimal("15.00"), VINGT_ET_UN)).isEqualByComparingTo("12.40");

        List<Tranche> tranches = CalculTva.ventiler(List.of(
                ligne("12.40", VINGT_ET_UN, 1), ligne("12.40", new BigDecimal("21"), 1)));

        assertThat(tranches).hasSize(1);
        assertThat(tranches.get(0).base()).isEqualByComparingTo("24.79");
        assertThat(tranches.get(0).tva()).isEqualByComparingTo("5.21");
    }

    @Test
    @DisplayName("document à deux taux : une tranche par taux, triées, totaux par addition des tranches")
    void deuxTaux() {
        List<Tranche> tranches = CalculTva.ventiler(List.of(
                ligne("12.40", VINGT_ET_UN, 2), ligne("10.00", SIX, 3)));

        assertThat(tranches).extracting(tranche -> tranche.taux().toPlainString()).containsExactly("6", "21");
        assertThat(tranches.get(0).base()).isEqualByComparingTo("30.00");
        assertThat(tranches.get(0).tva()).isEqualByComparingTo("1.80");
        assertThat(tranches.get(1).base()).isEqualByComparingTo("24.79");
        assertThat(tranches.get(1).tva()).isEqualByComparingTo("5.21");
        assertThat(CalculTva.totalHtva(tranches)).isEqualByComparingTo("54.79");
        assertThat(CalculTva.totalTva(tranches)).isEqualByComparingTo("7.01");
    }

    @Test
    @DisplayName("taux nul : la base est le total, la TVA est nulle")
    void tauxNul() {
        Tranche tranche = CalculTva.ventiler(List.of(ligne("40.00", BigDecimal.ZERO, 2))).get(0);

        assertThat(tranche.base()).isEqualByComparingTo("80.00");
        assertThat(tranche.tva()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("document sans ligne : aucune tranche, totaux à zéro")
    void sansLigne() {
        assertThat(CalculTva.ventiler(List.of())).isEmpty();
        assertThat(CalculTva.totalHtva(List.of())).isEqualByComparingTo("0.00");
        assertThat(CalculTva.totalTva(List.of())).isEqualByComparingTo("0.00");
    }
}
