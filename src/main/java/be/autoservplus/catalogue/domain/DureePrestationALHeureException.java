package be.autoservplus.catalogue.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * La durée d'une prestation à l'heure est celle d'une unité, soixante minutes
 * ({@code ck_service_duree_heure}, V72). Elle ne sert à aucun créneau, la prestation ne se
 * réservant pas.
 */
public class DureePrestationALHeureException extends RefusTraduit {

    public DureePrestationALHeureException() {
        super("admin.catalogue.erreur.duree-heure",
                "Une prestation à l'heure dure %d minutes, la durée d'une unité.".formatted(Prestation.DUREE_UNITE_HEURE));
    }
}
