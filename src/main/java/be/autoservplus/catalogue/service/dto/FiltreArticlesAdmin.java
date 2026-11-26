package be.autoservplus.catalogue.service.dto;

import java.util.Set;

/**
 * Filtres des listes de prestations et de pièces du back-office, tels que reçus dans
 * l'adresse. Chaque champ nul ou vide laisse passer tous les articles.
 *
 * @param categorie code de la catégorie
 * @param actif     {@code true} les actifs, {@code false} les inactifs, nul les deux
 * @param recherche terme saisi, cherché dans le libellé et l'identifiant
 */
public record FiltreArticlesAdmin(String categorie, Boolean actif, String recherche) {

    private static final Set<Boolean> TOUS = Set.of(true, false);

    public static FiltreArticlesAdmin aucun() {
        return new FiltreArticlesAdmin(null, null, null);
    }

    /** Code de la catégorie pour la requête, chaîne vide pour toutes. */
    public String categorieOuToutes() {
        return categorie == null ? "" : categorie.trim();
    }

    /** Valeurs de {@code actif} retenues. */
    public Set<Boolean> actifs() {
        return actif == null ? TOUS : Set.of(actif);
    }

    /** Terme de recherche, chaîne vide quand rien n'est saisi. */
    public String termeOuVide() {
        return recherche == null ? "" : recherche;
    }

    /** Au moins un filtre saisi : une liste vide dit alors qu'aucun article ne correspond. */
    public boolean saisi() {
        return !categorieOuToutes().isEmpty() || actif != null || !termeOuVide().isBlank();
    }
}
