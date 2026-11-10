package be.autoservplus.reservation.service.dto;

import be.autoservplus.reservation.domain.PosteAtelier;

import java.util.UUID;

/** Poste d'atelier vu du back-office. */
public record PosteVue(UUID reference, String libelle, String description, short ordre, boolean actif) {

    public static PosteVue de(PosteAtelier poste) {
        return new PosteVue(poste.getReference(), poste.getLibelle(), poste.getDescription(),
                poste.getOrdre(), poste.isActif());
    }
}
