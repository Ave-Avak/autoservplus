package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.ModificationCommande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ModificationCommandeRepository extends JpaRepository<ModificationCommande, Long> {

    /** Historique d'une commande, du plus récent au plus ancien, auteur chargé. */
    @Query("""
           select m from ModificationCommande m
           left join fetch m.auteur
           where m.commande.reference = :commande
           order by m.horodatage desc, m.id desc
           """)
    List<ModificationCommande> historiqueDe(@Param("commande") UUID commande);
}
