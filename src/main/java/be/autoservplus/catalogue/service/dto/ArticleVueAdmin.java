package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;

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
 * prestation) valent {@code null}.</p>
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
        boolean actif) {

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
                prestation.isActif());
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
                piece.isActif());
    }
}
