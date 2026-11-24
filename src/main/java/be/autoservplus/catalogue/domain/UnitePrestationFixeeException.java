package be.autoservplus.catalogue.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * L'unité d'une prestation se choisit à la création et ne se modifie plus, comme la plaque
 * d'un véhicule (F10). Le garage qui veut facturer à l'heure crée une nouvelle prestation
 * et désactive l'ancienne.
 */
public class UnitePrestationFixeeException extends RefusTraduit {

    public UnitePrestationFixeeException(String code) {
        super("admin.catalogue.erreur.unite-fixee",
                "L'unité de la prestation %s se choisit à la création et ne se modifie plus.".formatted(code));
    }
}
