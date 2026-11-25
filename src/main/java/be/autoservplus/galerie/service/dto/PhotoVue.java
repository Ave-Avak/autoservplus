package be.autoservplus.galerie.service.dto;

import be.autoservplus.galerie.domain.Photo;

import java.util.UUID;

/**
 * Une image telle qu'elle s'affiche (BL-9).
 *
 * <p>Ni clé séquentielle ni chemin de stockage : le gabarit ne reçoit que la référence,
 * qu'il passe au contrôleur qui sert l'image. Aucune vue ne connaît donc l'emplacement
 * réel des fichiers, et aucune ne publie un numéro qu'on pourrait incrémenter.</p>
 */
public record PhotoVue(UUID reference, String texteAlt, short ordre) {

    public static PhotoVue de(Photo photo) {
        return new PhotoVue(photo.getReference(), photo.getTexteAlt(), photo.getOrdre());
    }
}
