package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Levee quand un ajout melangerait pieces et prestations dans un meme panier (F12).
 *
 * <p><b>Ce n est pas une contrainte d ergonomie, c est la garde qui evite la
 * retractation partielle.</b> Une commande mixte poserait un probleme insoluble en
 * V1 : une piece reste retractable quand un service pleinement execute sous
 * renonciation VI.53 ne l est plus. Annuler une partie de la commande demanderait de
 * lever {@code uq_avoir_facture} et d emettre un avoir partiel — precisement ce que
 * F30 a remis a la V2.</p>
 *
 * <p>Le message dit au membre quoi faire, pas ce que le systeme ne sait pas faire :
 * commander separement est une action, « natures incompatibles » n en est pas une.</p>
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
