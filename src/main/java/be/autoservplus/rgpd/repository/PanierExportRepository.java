package be.autoservplus.rgpd.repository;

import be.autoservplus.vente.domain.Panier;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Complement de lecture du panier pour l export (F22).
 *
 * <p>Le contenu du panier est lu par {@code PanierRepository.findByMembreEmail},
 * du module {@code vente} : sa requete charge deja le panier, ses lignes et leurs
 * pieces, exactement ce dont l export a besoin — la dupliquer ici n aurait servi
 * a rien.
 *
 * <p>Ce repository ne couvre que ce que cette requete ne peut pas rendre : la
 * <b>date d ajout</b> de chaque ligne. La colonne {@code ligne_panier.created_at}
 * est mappee sur l entite mais sans accesseur, et lui en ajouter un modifierait le
 * module {@code vente}. Une projection HQL lit l attribut persistant sans rien
 * toucher.
 */
public interface PanierExportRepository extends Repository<Panier, Long> {

    @Query("""
            SELECT new be.autoservplus.rgpd.repository.AjoutAuPanier(l.id, l.createdAt)
            FROM LignePanier l
            WHERE l.panier.id = :panierId
            ORDER BY l.id
            """)
    List<AjoutAuPanier> datesAjout(@Param("panierId") Long panierId);
}
