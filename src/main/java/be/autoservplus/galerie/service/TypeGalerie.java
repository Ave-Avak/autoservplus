package be.autoservplus.galerie.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;

import java.util.Arrays;

/**
 * Nature de la fiche qui porte une galerie, telle qu'elle s'écrit dans l'adresse
 * {@code /admin/galerie/{type}/{reference}}.
 */
public enum TypeGalerie {

    PRESTATIONS("prestations"),
    PIECES("pieces"),
    INTERVENTIONS("interventions");

    private final String chemin;

    TypeGalerie(String chemin) {
        this.chemin = chemin;
    }

    public String chemin() {
        return chemin;
    }

    /** Un type absent de l'énumération ne désigne aucune fiche : 404, comme une référence inconnue. */
    public static TypeGalerie depuisChemin(String chemin) {
        return Arrays.stream(values())
                .filter(type -> type.chemin.equals(chemin))
                .findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Type de galerie", chemin));
    }
}
