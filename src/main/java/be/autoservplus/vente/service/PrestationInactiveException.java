package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Levee a l ajout au panier d une prestation retiree du catalogue (F12).
 *
 * <p>Miroir exact de {@link PieceInactiveException} et de la meme regle RM-28 : on ne
 * vend pas ce qui n est plus propose. La desactivation d une prestation (patron
 * RM-29) doit donc valoir aussi pour le panier, pas seulement pour la fiche publique.</p>
 */
public class PrestationInactiveException extends RegleMetierException {

    public PrestationInactiveException(String libelle) {
        super("RM-28", "La prestation « %s » n'est plus proposée.".formatted(libelle));
    }
}
