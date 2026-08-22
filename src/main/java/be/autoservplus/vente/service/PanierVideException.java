package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Conversion F14 : un panier vide — ou jamais cree — n a rien a convertir.
 * Meme reponse dans les deux cas : l etat percu par le membre est identique.
 */
public class PanierVideException extends RegleMetierException {

    public PanierVideException() {
        super("Votre panier est vide.");
    }
}
