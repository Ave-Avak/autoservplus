package be.autoservplus.galerie.service.dto;

import be.autoservplus.galerie.domain.Photo;

/**
 * Une image telle qu elle s affiche (BL-9).
 *
 * <p>{@code chemin} n est PAS une URL : c est le chemin relatif de stockage, que le
 * gabarit passe au controleur de service d images. Aucun gabarit ne connait donc
 * l emplacement reel des fichiers, et les changer de volume ne touche aucune vue.</p>
 */
public record PhotoVue(Long id, String chemin, String texteAlt, short ordre) {

    public static PhotoVue de(Photo photo) {
        return new PhotoVue(photo.getId(), photo.getChemin(), photo.getTexteAlt(),
                photo.getOrdre());
    }
}
