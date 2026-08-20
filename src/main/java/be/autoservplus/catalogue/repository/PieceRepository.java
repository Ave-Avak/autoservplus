package be.autoservplus.catalogue.repository;

import be.autoservplus.catalogue.domain.Piece;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PieceRepository extends JpaRepository<Piece, Long> {

    Optional<Piece> findByReference(UUID reference);

    Optional<Piece> findByReferenceFabricant(String referenceFabricant);

    boolean existsByReferenceFabricant(String referenceFabricant);

    List<Piece> findByActifTrueOrderByLibelleAsc();

    List<Piece> findByCategorieCodeAndActifTrueOrderByLibelleAsc(String codeCategorie);

    /** Pieces dont le stock a atteint ou franchi le seuil d alerte. */
    @Query("SELECT p FROM Piece p WHERE p.actif = true AND p.quantiteStock <= p.seuilAlerte")
    List<Piece> enAlerteDeStock();

    @Query("""
            SELECT p FROM Piece p
            WHERE p.actif = true
              AND (LOWER(p.libelle) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(p.marque) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(p.referenceFabricant) LIKE LOWER(CONCAT('%', :terme, '%')))
            ORDER BY p.libelle
            """)
    Page<Piece> rechercher(@Param("terme") String terme, Pageable pagination);
}