package be.autoservplus.common.montant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seul endroit où se calculent un prix taxe comprise, le total d'une ligne et la
 * ventilation de la TVA d'un document. Le catalogue, le panier, les rendez-vous, les
 * interventions, la facture et l'avoir l'appellent tous : changer la formule se fait ici.
 *
 * <p><b>Le prix annoncé est le prix payé.</b> Le client est un consommateur, le prix qui
 * l'engage est le prix unitaire taxe comprise, arrondi au cent. Le total d'une ligne est
 * ce prix multiplié par la quantité. L'ancienne formule arrondissait le total hors TVA de
 * la ligne puis lui appliquait le taux : deux pièces affichées à 15,00 € se payaient
 * 30,01 €.</p>
 *
 * <p><b>La base se déduit du total, par taux et sur tout le document.</b> Pour chaque taux,
 * la base imposable vaut le total taxe comprise divisé par un plus le taux, arrondie au
 * cent, et la TVA est la différence. Deux filtres à 15,00 € donnent une base de 24,79 € et
 * une TVA de 5,21 €, qui est bien 21 % de la base. Déduire la TVA ligne par ligne aurait
 * donné 24,80 € et 5,20 €, un centime à côté du taux annoncé. Une ligne n'a donc ni base ni
 * TVA propres : seuls son prix unitaire hors TVA, son taux et son total taxe comprise ont
 * un sens.</p>
 *
 * <p>Tous les arrondis sont au cent, au demi supérieur. Aucun calcul en virgule
 * flottante.</p>
 */
public final class CalculTva {

    private static final BigDecimal CENT = new BigDecimal("100");

    private CalculTva() {
    }

    /** Un montant taxe comprise et le taux auquel il est soumis : une ligne, vue de la ventilation. */
    public record MontantTaxe(BigDecimal taux, BigDecimal tvac) {
    }

    /** Base imposable et TVA d'un document pour un taux. */
    public record Tranche(BigDecimal taux, BigDecimal base, BigDecimal tva) {

        public BigDecimal tvac() {
            return base.add(tva);
        }
    }

    /** Prix unitaire taxe comprise, arrondi au cent : celui que le catalogue affiche. */
    public static BigDecimal unitaireTvac(BigDecimal unitaireHtva, BigDecimal taux) {
        return unitaireHtva.multiply(coefficient(taux)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Total taxe comprise d'une ligne : le prix unitaire affiché, multiplié par la quantité. */
    public static BigDecimal totalTvac(BigDecimal unitaireHtva, BigDecimal taux, int quantite) {
        return unitaireTvac(unitaireHtva, taux).multiply(BigDecimal.valueOf(quantite));
    }

    /** Base imposable d'un total taxe comprise soumis à un seul taux. */
    public static BigDecimal base(BigDecimal totalTvac, BigDecimal taux) {
        return totalTvac.divide(coefficient(taux), 2, RoundingMode.HALF_UP);
    }

    /**
     * Ventilation d'un document : une tranche par taux, calculée sur la somme des montants
     * de ce taux et non montant par montant. Tranches triées par taux croissant, pour que
     * l'ordre d'un document ne dépende pas de celui de ses lignes.
     */
    public static List<Tranche> ventiler(Collection<MontantTaxe> montants) {
        // Indexé par la valeur textuelle du taux : « 21 » et « 21.00 » désignent le même
        // taux, un BigDecimal les distinguerait par leur échelle.
        Map<String, MontantTaxe> parTaux = new LinkedHashMap<>();
        for (MontantTaxe montant : montants) {
            BigDecimal taux = montant.taux().stripTrailingZeros();
            parTaux.merge(taux.toPlainString(), new MontantTaxe(taux, montant.tvac()),
                    (cumul, ajout) -> new MontantTaxe(taux, cumul.tvac().add(ajout.tvac())));
        }
        List<Tranche> tranches = new ArrayList<>();
        for (MontantTaxe cumul : parTaux.values()) {
            BigDecimal base = base(cumul.tvac(), cumul.taux());
            tranches.add(new Tranche(cumul.taux(), base, cumul.tvac().subtract(base)));
        }
        tranches.sort(Comparator.comparing(Tranche::taux));
        return List.copyOf(tranches);
    }

    /** Somme des bases d'une ventilation. */
    public static BigDecimal totalHtva(List<Tranche> tranches) {
        return tranches.stream().map(Tranche::base).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Somme des TVA d'une ventilation. */
    public static BigDecimal totalTva(List<Tranche> tranches) {
        return tranches.stream().map(Tranche::tva).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Un plus le taux : 1,21 pour 21 %. */
    private static BigDecimal coefficient(BigDecimal taux) {
        return BigDecimal.ONE.add(taux.divide(CENT, 4, RoundingMode.HALF_UP));
    }
}
