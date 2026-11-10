package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Refus d'une écriture du planning (A24), rattaché au champ du formulaire qu'il vise.
 *
 * <p>Le chevauchement de deux plages et l'unicité du libellé d'un poste dépendent des
 * autres lignes : la base en juge, et ce refus porte sa traduction jusqu'au champ, sans
 * page d'erreur. {@code champ} est nul pour un refus qui ne vise aucun champ.</p>
 */
public class RefusPlanning extends RefusTraduit {

    private final String champ;

    public RefusPlanning(String champ, String cleMessage, String message) {
        super(cleMessage, message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
