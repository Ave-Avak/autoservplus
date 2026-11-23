package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Levée quand un ajout mélangerait pièces et prestations dans un même panier (F12).
 *
 * <p><b>Ce n'est pas une contrainte d'ergonomie, c'est la garde qui évite la
 * rétractation partielle.</b> Une commande mixte poserait un problème insoluble en
 * V1 : une pièce reste rétractable quand un service pleinement exécuté sous
 * renonciation VI.53 ne l'est plus. Annuler une partie de la commande demanderait de
 * lever {@code uq_avoir_facture} et d'émettre un avoir partiel, précisément ce que
 * F30 a remis à la V2.</p>
 *
 * <p>Le message dit au membre quoi faire, pas ce que le système ne sait pas faire :
 * commander séparément est une action, « natures incompatibles » n'en est pas une.</p>
 */
public class PanierDeNatureMixteException extends RegleMetierException {

    public PanierDeNatureMixteException(boolean ajoutDUnService) {
        super("RM-30", ajoutDUnService
                ? "Votre panier contient des pièces. Commandez-les d'abord, "
                        + "puis ajoutez vos prestations : les deux se règlent séparément."
                : "Votre panier contient des prestations. Commandez-les d'abord, "
                        + "puis ajoutez vos pièces : les deux se règlent séparément.");
    }
}
