package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * <b>RM-28</b> : seule une piece active peut etre ajoutee au panier. Une piece
 * devenue inactive APRES son ajout reste dans le panier (trace historique) et le
 * recapitulatif la signale — mais on ne peut plus en acquerir davantage.
 */
public class PieceInactiveException extends RegleMetierException {

    public PieceInactiveException(String libelle) {
        super("RM-28", "La piece « %s » n est plus proposee a la vente.".formatted(libelle));
    }
}
