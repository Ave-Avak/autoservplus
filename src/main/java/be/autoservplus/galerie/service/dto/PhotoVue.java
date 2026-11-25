package be.autoservplus.galerie.service.dto;

import be.autoservplus.catalogue.domain.TexteLocalise;
import be.autoservplus.galerie.domain.Photo;

import java.util.Locale;
import java.util.UUID;

/**
 * Une image telle qu'elle s'affiche (BL-9).
 *
 * <p>Ni clé séquentielle ni chemin de stockage : le gabarit ne reçoit que la référence,
 * qu'il passe au contrôleur qui sert l'image. Aucune vue ne connaît donc l'emplacement
 * réel des fichiers, et aucune ne publie un numéro qu'on pourrait incrémenter.</p>
 *
 * @param alternative texte alternatif dans la langue de l'écran, ou en français avec sa
 *                    langue quand il n'est pas traduit
 * @param largeur     largeur en pixels, nulle si l'en-tête n'a pas pu être lu
 * @param hauteur     hauteur en pixels, nulle si l'en-tête n'a pas pu être lu
 * @param vignette    vrai si une version de {@value #LARGEUR_VIGNETTE} pixels est servie
 */
public record PhotoVue(UUID reference, TexteLocalise alternative, short ordre,
                       Integer largeur, Integer hauteur, boolean vignette) {

    /** Largeur de la vignette des photos de démonstration (V79). */
    public static final int LARGEUR_VIGNETTE = 480;

    public static PhotoVue de(Photo photo, Locale langue) {
        return new PhotoVue(photo.getReference(), photo.texteAlt(langue), photo.getOrdre(),
                photo.getLargeur(), photo.getHauteur(), photo.getCheminVignette() != null);
    }

    public String texteAlt() {
        return alternative.texte();
    }

    /** Vrai quand les deux dimensions sont connues, seules à réserver la place de l'image. */
    public boolean dimensionsConnues() {
        return largeur != null && hauteur != null;
    }
}
