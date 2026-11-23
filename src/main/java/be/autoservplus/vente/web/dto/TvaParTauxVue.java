package be.autoservplus.vente.web.dto;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.util.List;

/**
 * TVA d'un taux, pour le bloc des totaux du panier et d'une commande : le taux sans
 * décimales inutiles (« 6 », « 21 ») et son montant dans la langue de l'écran.
 */
public record TvaParTauxVue(String taux, String montant) {

    public static List<TvaParTauxVue> de(List<CalculTva.Tranche> tranches) {
        return tranches.stream()
                .map(tranche -> new TvaParTauxVue(tranche.taux().stripTrailingZeros().toPlainString(),
                        FormatageRdv.eurosEcran(tranche.tva())))
                .toList();
    }
}
