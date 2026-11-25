package be.autoservplus.intervention.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Quantité refusée pour une ligne d'intervention (Q-H-37). Elle est strictement positive,
 * entière pour une pièce ou un forfait, en heures au centième pour une prestation à
 * l'heure, et tient dans {@code NUMERIC(7,2)}. L'écran affiche le message de sa clé.
 */
public class QuantiteLigneRefuseeException extends RefusTraduit {

    private QuantiteLigneRefuseeException(String cle, String message) {
        super(cle, message);
    }

    static QuantiteLigneRefuseeException nonPositive() {
        return new QuantiteLigneRefuseeException("intervention.erreur.quantite-positive",
                "La quantité doit être supérieure à zéro.");
    }

    static QuantiteLigneRefuseeException nonEntiere() {
        return new QuantiteLigneRefuseeException("intervention.erreur.quantite-entiere",
                "Une pièce ou une prestation au forfait se compte en unités entières.");
    }

    static QuantiteLigneRefuseeException tropPrecise() {
        return new QuantiteLigneRefuseeException("intervention.erreur.quantite-heures",
                "Un nombre d'heures porte au plus deux décimales et reste sous 100 000.");
    }
}
