package be.autoservplus.catalogue.domain;

import java.util.Objects;

/**
 * Prestation retenue par le client avec son choix des pièces, avant que la ligne ne fige
 * le prix qui en découle.
 *
 * @param choix {@code null} quand la prestation ne propose pas de choix
 */
public record PrestationChoisie(Prestation prestation, ChoixPieces choix) {

    public PrestationChoisie {
        Objects.requireNonNull(prestation, "prestation");
    }

    /** La prestation avec le choix du back-office : pièces fournies si le choix existe. */
    public static PrestationChoisie parDefaut(Prestation prestation) {
        return new PrestationChoisie(prestation, prestation.choixParDefaut());
    }
}
