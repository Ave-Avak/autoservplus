package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Conversion F14 : un panier vide (ou jamais créé) n'a rien à convertir.
 * Même réponse dans les deux cas : l'état perçu par le membre est identique.
 */
public class PanierVideException extends RegleMetierException {

    public PanierVideException() {
        super("Votre panier est vide.");
    }
}
