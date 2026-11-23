package be.autoservplus.rgpd.repository;

import java.time.Instant;

/**
 * Instant d'ajout d'une ligne au panier, lu par projection.
 *
 * <p>Existe pour une seule raison : {@code ligne_panier.created_at} est bien
 * mappé sur l'entité {@code LignePanier}, mais celle-ci n'en expose aucun
 * accesseur. Ajouter un {@code getCreatedAt()} modifierait le module
 * {@code vente}. Une projection HQL lit l'attribut persistant directement,
 * sans toucher à l'entité.
 *
 * @param ligneId   identifiant de la ligne, clef de rapprochement
 * @param dateAjout instant d'ajout au panier
 */
public record AjoutAuPanier(Long ligneId, Instant dateAjout) {
}
