package be.autoservplus.catalogue.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Prix « pose seule » refusé : négatif, égal ou supérieur au prix pièces comprises, ou
 * posé sur une prestation à l'heure, qui se facture au temps passé sans pièces comprises.
 * Les mêmes règles sont portées par {@code ck_service_pose_seule} et
 * {@code ck_service_pose_seule_forfait} (V76).
 */
public class PrixPoseSeuleRefuseException extends RefusTraduit {

    private PrixPoseSeuleRefuseException(String cle, String message) {
        super(cle, message);
    }

    static PrixPoseSeuleRefuseException pasInferieur(String code) {
        return new PrixPoseSeuleRefuseException("admin.catalogue.erreur.pose-seule-prix",
                "Le prix pose seule de la prestation %s doit être positif ou nul, et inférieur au prix pièces comprises."
                        .formatted(code));
    }

    static PrixPoseSeuleRefuseException aLHeure(String code) {
        return new PrixPoseSeuleRefuseException("admin.catalogue.erreur.pose-seule-heure",
                "La prestation %s se facture à l'heure : elle n'a pas de prix pose seule.".formatted(code));
    }
}
