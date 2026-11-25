package be.autoservplus.api.web.dto;

import be.autoservplus.catalogue.service.dto.ArticleVue;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une prestation telle que l API publique l expose (BL-8).
 *
 * <p><b>DTO propre a l API, et non reutilisation d {@code ArticleVue}.</b> Cette
 * derniere sert les gabarits Thymeleaf : ses champs peuvent changer au gre d un
 * ecran, ce qui casserait silencieusement le contrat d une API publique que des tiers
 * consomment. La duplication est ici le prix de la stabilite.</p>
 *
 * <p>Aucune donnee de gestion : ni stock, ni marge, ni seuil d alerte. Seul ce qu un
 * visiteur voit deja sur la fiche publique est repris.</p>
 *
 * @param unite {@code FORFAIT} ou {@code HEURE} (V72) : le prix d'une prestation à l'heure est
 *              un taux horaire, et sa durée vaut une heure
 * @param prixPoseSeuleHtva prix quand le client apporte ses pièces (V76), {@code null} quand
 *                          le garage ne propose pas ce choix
 */
public record PrestationPublique(
        UUID reference,
        String code,
        String libelle,
        String description,
        String categorie,
        BigDecimal prixHtva,
        BigDecimal prixTvac,
        Integer dureeMinutes,
        String unite,
        BigDecimal prixPoseSeuleHtva,
        BigDecimal prixPoseSeuleTvac) {

    public static PrestationPublique de(ArticleVue vue) {
        return new PrestationPublique(vue.reference(), vue.code(), vue.libelle(),
                vue.description(), vue.categorie(), vue.prixHtva(), vue.prixTvac(),
                vue.dureeMinutes(), vue.unite(), vue.prixPoseSeuleHtva(), vue.prixPoseSeuleTvac());
    }
}
