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

/**
 * Acces aux prestations du catalogue.
 *
 * <p>Les requetes de consultation chargent la categorie par JOIN FETCH dans la meme
 * requete. Sans cela, la conversion en objet de transfert echouerait hors transaction,
 * open-in-view etant desactive, ou declencherait une requete supplementaire par ligne.</p>
 */
public interface PrestationRepository extends JpaRepository<Prestation, Long> {

    Optional<Prestation> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT p FROM Prestation p JOIN FETCH p.categorie WHERE p.reference = :reference")
    Optional<Prestation> findByReference(@Param("reference") UUID reference);

    @Query("SELECT p FROM Prestation p JOIN FETCH p.categorie WHERE p.actif = true ORDER BY p.libelle")
    List<Prestation> findByActifTrueOrderByLibelleAsc();

    @Query("""
            SELECT p FROM Prestation p JOIN FETCH p.categorie c
            WHERE c.code = :codeCategorie AND p.actif = true
            ORDER BY p.libelle
            """)
    List<Prestation> findByCategorieCodeAndActifTrueOrderByLibelleAsc(
            @Param("codeCategorie") String codeCategorie);

    /** Recherche insensible a la casse sur le libelle et la description. */
    @Query("""
            SELECT p FROM Prestation p JOIN FETCH p.categorie
            WHERE p.actif = true
              AND (LOWER(p.libelle) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :terme, '%')))
            ORDER BY p.libelle
            """)
    Page<Prestation> rechercher(@Param("terme") String terme, Pageable pagination);
}