package be.autoservplus.catalogue.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * <b>RM-29</b> : la suppression definitive d un element du catalogue est refusee
 * car un historique (reservation, panier, commande, intervention) le reference
 * encore. Seule la desactivation (RM-28) est alors permise : l element disparait
 * du catalogue public mais reste lisible depuis les documents qui le citent.
 */
public class SuppressionRefuseeException extends RegleMetierException {

    private final long nombreReferences;

    public SuppressionRefuseeException(String libelle, long nombreReferences) {
        super("RM-29", "« %s » est reference par %d ligne(s) d historique : seule la desactivation est permise."
                .formatted(libelle, nombreReferences));
        this.nombreReferences = nombreReferences;
    }

    public long getNombreReferences() {
        return nombreReferences;
    }
}
