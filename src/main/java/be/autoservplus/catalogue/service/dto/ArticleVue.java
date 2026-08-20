package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vue d un element du catalogue destinee a l affichage.
 *
 * <p>Les entites ne sont jamais transmises aux vues : cela evite d exposer des champs
 * internes et empeche le declenchement de chargements paresseux hors transaction. Le
 * meme objet pourra etre serialise en JSON le jour ou une API sera exposee.</p>
 */
public record ArticleVue(
        UUID reference,
        String code,
        String libelle,
        String description,
        String categorie,
        BigDecimal prixHtva,
        BigDecimal prixTvac,
        Integer dureeMinutes,
        String marque,
        boolean disponible) {

    public static ArticleVue de(Prestation prestation) {
        return new ArticleVue(
                prestation.getReference(),
                prestation.getCode(),
                prestation.getLibelle(),
                prestation.getDescription(),
                prestation.getCategorie().getLibelle(),
                prestation.getPrixHtva(),
                prestation.prixTvac(),
                prestation.getDureeMinutes(),
                null,
                prestation.isActif());
    }

    public static ArticleVue de(Piece piece) {
        return new ArticleVue(
                piece.getReference(),
                piece.getReferenceFabricant(),
                piece.getLibelle(),
                piece.getDescription(),
                piece.getCategorie().getLibelle(),
                piece.getPrixHtva(),
                piece.prixTvac(),
                null,
                piece.getMarque(),
                piece.estDisponible());
    }

    /** Duree formatee pour l affichage : « 1 h 30 » plutot que « 90 minutes ». */
    public String dureeLisible() {
        if (dureeMinutes == null) {
            return null;
        }
        int heures = dureeMinutes / 60;
        int minutes = dureeMinutes % 60;
        if (heures == 0) return minutes + " min";
        if (minutes == 0) return heures + " h";
        return "%d h %02d".formatted(heures, minutes);
    }
}