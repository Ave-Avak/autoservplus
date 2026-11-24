package be.autoservplus.catalogue.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Une prestation à l'heure ne se réserve pas en rendez-vous et ne se met pas au panier
 * (Q-H-36) : le client ne sait pas combien d'heures il faut, l'atelier l'ajoute pendant
 * l'intervention. Le devis F15 naît du panier, il est couvert par le même refus.
 */
public class PrestationALHeureRefuseeException extends RefusTraduit {

    public PrestationALHeureRefuseeException(String libelle) {
        super(null, "catalogue.erreur.prestation-a-l-heure", new Object[]{libelle},
                "La prestation « %s » se facture à l'heure par l'atelier : elle ne se réserve pas et ne s'achète pas en ligne."
                        .formatted(libelle));
    }
}
