package be.autoservplus.api.web.dto;

import be.autoservplus.catalogue.domain.Rayon;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Un produit du catalogue (pièce auto, accessoire ou produit d'entretien) tel que l'API
 * publique l'expose.
 *
 * <p>DTO propre à l'API, pour la même raison que {@link PrestationPublique}. La quantité en
 * stock reste une donnée interne du garage : un tiers qui affiche le catalogue n'a besoin
 * que de savoir si l'article est disponible.</p>
 *
 * @param code      référence du fabricant
 * @param categorie libellé de la catégorie dans la langue négociée
 * @param liens     liens HAL du produit (F41) : lui-même, sa collection, le garage et sa
 *                  fiche sur le site
 */
public record ArticlePublic(
        UUID reference,
        String code,
        String libelle,
        String description,
        Rayon rayon,
        String categorie,
        String marque,
        BigDecimal prixHtva,
        BigDecimal prixTvac,
        boolean disponible,
        @JsonProperty("_links") Map<String, Lien> liens) {

    public static ArticlePublic de(ArticleVue vue, Map<String, Lien> liens) {
        return new ArticlePublic(vue.reference(), vue.code(), vue.libelle(), vue.description(),
                vue.rayon(), vue.categorie(), vue.marque(), vue.prixHtva(), vue.prixTvac(), vue.disponible(),
                liens);
    }
}
