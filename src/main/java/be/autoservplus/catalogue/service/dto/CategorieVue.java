package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.TexteLocalise;

import java.util.Locale;

/**
 * Catégorie du catalogue telle que le filtre public l'affiche.
 *
 * @param langueLibelle langue à déclarer sur le libellé s'il est affiché en repli, ou
 *                      {@code null} quand il est dans la langue de la page
 */
public record CategorieVue(String code, String libelle, String langueLibelle) {

    public static CategorieVue de(Categorie categorie, Locale langue) {
        TexteLocalise libelle = categorie.libelle(langue);
        return new CategorieVue(categorie.getCode(), libelle.texte(), libelle.langueSiRepli(langue));
    }
}
