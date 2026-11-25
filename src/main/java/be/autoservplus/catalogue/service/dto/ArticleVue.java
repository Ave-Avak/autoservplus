package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TexteLocalise;
import be.autoservplus.i18n.LanguesSupportees;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Vue d un element du catalogue destinee a l affichage.
 *
 * <p>Les entites ne sont jamais transmises aux vues : cela evite d exposer des champs
 * internes et empeche le declenchement de chargements paresseux hors transaction. Le
 * meme objet pourra etre serialise en JSON le jour ou une API sera exposee.</p>
 *
 * <p>Le libellé, la description et la catégorie sont rendus dans la langue demandée, ou
 * en français quand leur traduction manque. Les trois champs {@code langue...} disent
 * alors au gabarit quelle langue déclarer sur l'élément ({@code lang="fr"}, WCAG 3.1.2).
 * Ils valent {@code null} quand le texte est dans la langue de la page : c'est ici, et
 * ici seulement, que le repli se décide.</p>
 *
 * @param langueLibelle     langue à déclarer sur le libellé s'il est affiché en repli
 * @param langueDescription langue à déclarer sur la description affichée en repli
 * @param langueCategorie   langue à déclarer sur la catégorie affichée en repli
 * @param unite             {@code FORFAIT} ou {@code HEURE} pour une prestation, {@code null}
 *                          pour une pièce
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
        boolean disponible,
        String langueLibelle,
        String langueDescription,
        String langueCategorie,
        String unite) {

    /** Un article dont tous les textes sont dans la langue de la page. */
    public ArticleVue(UUID reference, String code, String libelle, String description, String categorie,
                      BigDecimal prixHtva, BigDecimal prixTvac, Integer dureeMinutes, String marque,
                      boolean disponible) {
        this(reference, code, libelle, description, categorie, prixHtva, prixTvac, dureeMinutes, marque,
                disponible, null, null, null, null);
    }

    /**
     * Prestation facturée à l'heure par l'atelier : le prix est un taux horaire, sans durée
     * affichée, ni réservation, ni ajout au panier (Q-H-36).
     */
    public boolean aLHeure() {
        return "HEURE".equals(unite);
    }

    /** La prestation en français, langue d'origine du catalogue. */
    public static ArticleVue de(Prestation prestation) {
        return de(prestation, LanguesSupportees.DEFAUT);
    }

    public static ArticleVue de(Prestation prestation, Locale langue) {
        TexteLocalise libelle = prestation.libelle(langue);
        TexteLocalise description = prestation.description(langue);
        TexteLocalise categorie = prestation.getCategorie().libelle(langue);
        return new ArticleVue(
                prestation.getReference(),
                prestation.getCode(),
                libelle.texte(),
                description.texte(),
                categorie.texte(),
                prestation.getPrixHtva(),
                prestation.prixTvac(),
                prestation.getDureeMinutes(),
                null,
                prestation.isActif(),
                libelle.langueSiRepli(langue),
                description.langueSiRepli(langue),
                categorie.langueSiRepli(langue),
                prestation.getUnite().name());
    }

    /** La pièce en français, langue d'origine du catalogue. */
    public static ArticleVue de(Piece piece) {
        return de(piece, LanguesSupportees.DEFAUT);
    }

    public static ArticleVue de(Piece piece, Locale langue) {
        TexteLocalise libelle = piece.libelle(langue);
        TexteLocalise description = piece.description(langue);
        TexteLocalise categorie = piece.getCategorie().libelle(langue);
        return new ArticleVue(
                piece.getReference(),
                piece.getReferenceFabricant(),
                libelle.texte(),
                description.texte(),
                categorie.texte(),
                piece.getPrixHtva(),
                piece.prixTvac(),
                null,
                piece.getMarque(),
                piece.estDisponible(),
                libelle.langueSiRepli(langue),
                description.langueSiRepli(langue),
                categorie.langueSiRepli(langue),
                null);
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
