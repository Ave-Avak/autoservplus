package be.autoservplus.catalogue.domain;

import java.util.Locale;

/**
 * Rayon du catalogue public, porté par une catégorie de produits (colonne
 * {@code categorie.rayon}, V78). Une catégorie de prestations n'en a pas.
 *
 * <p>L'ordre des constantes est celui des onglets du catalogue, après les prestations.
 * Le préfixe donne l'adresse de la liste et des fiches du rayon.</p>
 */
public enum Rayon {

    PIECES_AUTO("pieces"),
    ACCESSOIRES("accessoires"),
    ENTRETIEN("entretien");

    private final String prefixe;

    Rayon(String prefixe) {
        this.prefixe = prefixe;
    }

    /** Segment d'adresse de la liste et des fiches : {@code /pieces}, {@code /accessoires}... */
    public String prefixe() {
        return prefixe;
    }

    /** Clé i18n du nom du rayon (onglet, fil d'Ariane, recherche). */
    public String cleLibelle() {
        return "catalogue.rayon." + name().toLowerCase(Locale.ROOT);
    }
}
