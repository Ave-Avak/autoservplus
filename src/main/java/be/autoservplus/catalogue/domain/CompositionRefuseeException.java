package be.autoservplus.catalogue.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Composition refusée : quantité nulle ou négative, ou pièce ajoutée à une prestation à
 * l'heure, dont les pièces se facturent à part, en lignes de pièce de l'intervention.
 */
public class CompositionRefuseeException extends RefusTraduit {

    private CompositionRefuseeException(String cle, String message) {
        super(cle, message);
    }

    static CompositionRefuseeException quantite(String code) {
        return new CompositionRefuseeException("admin.catalogue.erreur.composition-quantite",
                "Une pièce de la composition de %s compte au moins une unité.".formatted(code));
    }

    static CompositionRefuseeException aLHeure(String code) {
        return new CompositionRefuseeException("admin.catalogue.erreur.composition-heure",
                "La prestation %s se facture à l'heure : elle n'a pas de composition.".formatted(code));
    }
}
