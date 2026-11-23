package be.autoservplus.facturation.service;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.vente.domain.LignePanier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Ventilation de la TVA par taux, mention obligatoire d une facture belge des lors
 * que plusieurs taux coexistent (AR n°1, art. 5 : base imposable ventilee par taux).
 *
 * <p><b>Chaque tranche se calcule sur le total taxe comprise de son taux</b>, par
 * {@link CalculTva}, exactement comme le panier a calculé les montants que la commande a
 * figés : le total des tranches égale donc au centime les montants de la facture. La base
 * n'est pas la somme de bases de ligne, une ligne n'en a pas.</p>
 *
 * <p>Tranches triees par taux croissant : l ordre d affichage d une facture est
 * fixe, il ne doit pas dependre de l ordre des lignes du panier.</p>
 */
public record VentilationTva(List<TrancheTva> tranches) {

    /** Une base imposable et sa TVA, pour un taux donne. */
    public record TrancheTva(BigDecimal taux, BigDecimal baseHtva, BigDecimal montantTva) {

        public BigDecimal montantTvac() {
            return baseHtva.add(montantTva);
        }
    }

    public static VentilationTva desLignes(List<LignePanier> lignes) {
        return new VentilationTva(CalculTva.ventiler(lignes.stream()
                        .map(ligne -> new CalculTva.MontantTaxe(ligne.getTauxTva(), ligne.totalTvac()))
                        .toList()).stream()
                .map(tranche -> new TrancheTva(tranche.taux(), tranche.base(), tranche.tva()))
                .toList());
    }

    /**
     * Le taux de la facture s il est unique.
     *
     * @return le taux, ou {@link Optional#empty()} si la facture melange plusieurs
     *         taux : la colonne {@code taux_tva_applique} vaut alors NULL, la
     *         ventilation faisant seule foi (V26)
     */
    public Optional<BigDecimal> tauxUnique() {
        return tranches.size() == 1
                ? Optional.of(tranches.get(0).taux())
                : Optional.empty();
    }

    public boolean estMultiTaux() {
        return tranches.size() > 1;
    }

    public BigDecimal totalHtva() {
        return somme(TrancheTva::baseHtva);
    }

    public BigDecimal totalTva() {
        return somme(TrancheTva::montantTva);
    }

    public BigDecimal totalTvac() {
        return totalHtva().add(totalTva());
    }

    private BigDecimal somme(java.util.function.Function<TrancheTva, BigDecimal> montant) {
        return tranches.stream().map(montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
