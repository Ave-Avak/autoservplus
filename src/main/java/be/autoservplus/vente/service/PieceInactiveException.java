package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Contrainte de <b>F13</b> : seule une piece active peut etre ajoutee au panier.
 * Une piece devenue inactive APRES son ajout reste dans le panier et le
 * recapitulatif la signale — mais on ne peut plus en acquerir davantage.
 *
 * <p>Pas de code RM : la contrainte est portee par la fonctionnalite F13 du CdC,
 * pas par une regle metier numerotee — d ou le constructeur sans code.</p>
 */
public class PieceInactiveException extends RegleMetierException {

    public PieceInactiveException(String libelle) {
        super("La piece « %s » n est plus proposee a la vente.".formatted(libelle));
    }
}
