package be.autoservplus.catalogue.repository;

import be.autoservplus.catalogue.domain.Prestation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrestationRepository extends JpaRepository<Prestation, Long> {

    Optional<Prestation> findByReference(UUID reference);

    Optional<Prestation> findByCode(String code);

    boolean existsByCode(String code);

    List<Prestation> findByActifTrueOrderByLibelleAsc();

    List<Prestation> findByCategorieCodeAndActifTrueOrderByLibelleAsc(String codeCategorie);

    /**
     * Recherche insensible a la casse et aux accents sur le libelle et la description.
     *
     * <p>La fonction unaccent de PostgreSQL permet de trouver « decalaminage » en tapant
     * « décalaminage » et inversement.</p>
     */
    @Query("""
            SELECT p FROM Prestation p
            WHERE p.actif = true
              AND (LOWER(p.libelle) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :terme, '%')))
            ORDER BY p.libelle
            """)
    Page<Prestation> rechercher(@Param("terme") String terme, Pageable pagination);
}