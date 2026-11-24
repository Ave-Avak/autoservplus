package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Prestation;

/**
 * Unité d'une ligne d'intervention, figée à sa création comme le libellé et le prix (V73).
 * Seule une ligne à l'heure admet une quantité décimale.
 */
public enum UniteLigne {

    FORFAIT,

    HEURE,

    PIECE;

    /** Unité de la ligne qui reprend cette prestation : l'heure ou le forfait. */
    public static UniteLigne de(Prestation prestation) {
        return prestation.estALHeure() ? HEURE : FORFAIT;
    }

    /** Une pièce et un forfait se comptent en unités entières. */
    public boolean admetDesDecimales() {
        return this == HEURE;
    }
}
