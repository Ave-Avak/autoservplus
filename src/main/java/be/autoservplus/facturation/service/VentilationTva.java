package be.autoservplus.facturation.service;

import be.autoservplus.vente.domain.LignePanier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ventilation de la TVA par taux, mention obligatoire d une facture belge des lors
 * que plusieurs taux coexistent (AR n°1, art. 5 : base imposable ventilee par taux).
 *
 * <p><b>Les tranches somment les montants deja calcules ligne a ligne, elles ne les
 * recalculent pas.</b> C est la seule facon de garantir que le total des tranches
 * egale au centime les montants figes de la commande : chaque ligne definit sa TVA
 * comme TVAC moins HTVA (RM-30), et refaire le calcul depuis la base cumulee d une
 * tranche produirait, sur certains arrondis, un centime d ecart entre la facture et
 * le document PDF qui la presente.</p>
 *
 * <p>Tranches triees par taux croissant : l ordre d affichage d une facture est
 * fixe, il ne doit pas dependre de l ordre des lignes du panier.</p>
 */
public record VentilationTva(List<TrancheTva> tranches) {

    /** Une base imposable et sa TVA, pour un taux donne. */
    public record TrancheTva(BigDecimal taux, BigDecimal baseHtva, BigDecimal montantTva) {

        TrancheTva ajouter(BigDecimal htva, BigDecimal tva) {
            return new TrancheTva(taux, baseHtva.add(htva), montantTva.add(tva));
        }

        public BigDecimal montantTvac() {
            return baseHtva.add(montantTva);
        }
    }

    public static VentilationTva desLignes(List<LignePanier> lignes) {
        // LinkedHashMap indexee par la valeur textuelle du taux : « 21 » et « 21.00 »
        // designent le meme taux, un BigDecimal les distinguerait par leur echelle.
        Map<String, TrancheTva> parTaux = new LinkedHashMap<>();
        for (LignePanier ligne : lignes) {
            BigDecimal taux = ligne.getTauxTva().stripTrailingZeros();
            parTaux.merge(taux.toPlainString(),
                    new TrancheTva(taux, ligne.totalHtva(), ligne.totalTva()),
                    (existante, ajout) -> existante.ajouter(ajout.baseHtva(), ajout.montantTva()));
        }
        List<TrancheTva> triees = new ArrayList<>(parTaux.values());
        triees.sort(Comparator.comparing(TrancheTva::taux));
        return new VentilationTva(List.copyOf(triees));
    }

    /**
     * Le taux de la facture s il est unique.
     *
     * @return le taux, ou {@link Optional#empty()} si la facture melange plusieurs
     *         taux — la colonne {@code taux_tva_applique} vaut alors NULL, la
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
