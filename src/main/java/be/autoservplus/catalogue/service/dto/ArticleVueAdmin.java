package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.Traductions;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vue back-office d un element du catalogue.
 *
 * <p>Contrairement a {@link ArticleVue}, destinee au visiteur, cette vue expose
 * l etat d administration : le drapeau {@code actif} brut (et non la disponibilite
 * commerciale), le stock et le seuil d alerte, ainsi que tous les champs necessaires
 * au pre-remplissage du formulaire de modification. Les champs sans objet pour un
 * type d article ({@code dureeMinutes} d une piece, {@code quantiteStock} d une
 * prestation) valent {@code null}. {@code unite} vaut {@code FORFAIT} ou {@code HEURE} pour une
 * prestation, {@code null} pour une pièce.</p>
 */
public record ArticleVueAdmin(
        UUID reference,
        String identifiant,
        String libelle,
        String description,
        String categorieCode,
        String categorieLibelle,
        BigDecimal prixHtva,
        BigDecimal prixTvac,
        BigDecimal tauxTva,
        Integer dureeMinutes,
        String marque,
        Integer quantiteStock,
        Integer seuilAlerte,
        boolean actif,
        Traductions traductions,
        String unite) {

    /** Un article sans aucune traduction. */
    public ArticleVueAdmin(UUID reference, String identifiant, String libelle, String description,
                           String categorieCode, String categorieLibelle, BigDecimal prixHtva, BigDecimal prixTvac,
                           BigDecimal tauxTva, Integer dureeMinutes, String marque, Integer quantiteStock,
                           Integer seuilAlerte, boolean actif) {
        this(reference, identifiant, libelle, description, categorieCode, categorieLibelle, prixHtva, prixTvac,
                tauxTva, dureeMinutes, marque, quantiteStock, seuilAlerte, actif, Traductions.aucune(), null);
    }

    /** Prestation facturée à l'heure : « /h » au lieu d'une durée. */
    public boolean aLHeure() {
        return "HEURE".equals(unite);
    }

    /** Vrai quand le libellé existe en néerlandais et en anglais : sinon le site l'affiche en français. */
    public boolean traduit() {
        return traductions.libelleComplet();
    }

    public static ArticleVueAdmin de(Prestation prestation) {
        return new ArticleVueAdmin(
                prestation.getReference(),
                prestation.getCode(),
                prestation.getLibelle(),
                prestation.getDescription(),
                prestation.getCategorie().getCode(),
                prestation.getCategorie().getLibelle(),
                prestation.getPrixHtva(),
                prestation.prixTvac(),
                prestation.getTauxTva(),
                prestation.getDureeMinutes(),
                null,
                null,
                null,
                prestation.isActif(),
                prestation.getTraductions(),
                prestation.getUnite().name());
    }

    public static ArticleVueAdmin de(Piece piece) {
        return new ArticleVueAdmin(
                piece.getReference(),
                piece.getReferenceFabricant(),
                piece.getLibelle(),
                piece.getDescription(),
                piece.getCategorie().getCode(),
                piece.getCategorie().getLibelle(),
                piece.getPrixHtva(),
                piece.prixTvac(),
                piece.getTauxTva(),
                null,
                piece.getMarque(),
                piece.getQuantiteStock(),
                piece.getSeuilAlerte(),
                piece.isActif(),
                piece.getTraductions(),
                null);
    }
}
