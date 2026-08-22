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

/**
 * Acces aux pieces detachees du catalogue.
 *
 * <p>Les requetes de consultation chargent la categorie par JOIN FETCH dans la meme
 * requete. Sans cela, l affichage d une liste de n pieces declencherait n requetes
 * supplementaires, une par categorie : c est le probleme dit N+1.</p>
 */
public interface PieceRepository extends JpaRepository<Piece, Long> {

    Optional<Piece> findByReferenceFabricant(String referenceFabricant);

    boolean existsByReferenceFabricant(String referenceFabricant);

    @Query("SELECT p FROM Piece p JOIN FETCH p.categorie WHERE p.reference = :reference")
    Optional<Piece> findByReference(@Param("reference") UUID reference);

    @Query("SELECT p FROM Piece p JOIN FETCH p.categorie WHERE p.actif = true ORDER BY p.libelle")
    List<Piece> findByActifTrueOrderByLibelleAsc();

    @Query("""
            SELECT p FROM Piece p JOIN FETCH p.categorie c
            WHERE c.code = :codeCategorie AND p.actif = true
            ORDER BY p.libelle
            """)
    List<Piece> findByCategorieCodeAndActifTrueOrderByLibelleAsc(
            @Param("codeCategorie") String codeCategorie);

    /** Pieces dont le stock a atteint ou franchi le seuil d alerte. */
    @Query("""
            SELECT p FROM Piece p JOIN FETCH p.categorie
            WHERE p.actif = true AND p.quantiteStock <= p.seuilAlerte
            ORDER BY p.libelle
            """)
    List<Piece> enAlerteDeStock();

    /**
     * Verrou pessimiste (SELECT FOR UPDATE) pour le decrement du stock au paiement
     * confirme : la table piece n a pas de colonne version, et deux webhooks payes
     * portant sur la meme piece doivent se serialiser plutot que se perdre. Verrouiller
     * les pieces dans un ordre deterministe (id croissant) — voir CommandeRepository#lignesDe.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Piece p WHERE p.id = :id")
    Optional<Piece> verrouillerParId(@Param("id") Long id);

    /** Recherche insensible a la casse sur le libelle, la marque et la reference fabricant. */
    @Query("""
            SELECT p FROM Piece p JOIN FETCH p.categorie
            WHERE p.actif = true
              AND (LOWER(p.libelle) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(p.marque) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(p.referenceFabricant) LIKE LOWER(CONCAT('%', :terme, '%')))
            ORDER BY p.libelle
            """)
    Page<Piece> rechercher(@Param("terme") String terme, Pageable pagination);
}