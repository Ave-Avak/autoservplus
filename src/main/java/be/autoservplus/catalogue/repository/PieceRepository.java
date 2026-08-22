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

    /** Catalogue complet pour le back-office, actifs et inactifs confondus. */
    @Query("SELECT p FROM Piece p JOIN FETCH p.categorie ORDER BY p.libelle")
    List<Piece> catalogueComplet();

    /**
     * Nombre de lignes d historique referencant la piece (RM-29) : lignes de panier
     * ou de commande ({@code ligne_panier}) et lignes d intervention. Requete native :
     * elle est le miroir applicatif exact des FK {@code ON DELETE RESTRICT}
     * ({@code fk_ligne_panier_piece}, {@code fk_ligne_interv_piece}) sans imposer au
     * module catalogue de dependre des entites des modules vente et intervention.
     * La table {@code photo}, en CASCADE, est une illustration propre a la piece et
     * ne bloque pas. Toute nouvelle table referencant {@code piece} doit s ajouter
     * ici — en cas d oubli, le RESTRICT en base refuse quand meme la suppression.
     */
    @Query(value = """
            SELECT (SELECT count(*) FROM ligne_panier WHERE piece_id = :id)
                 + (SELECT count(*) FROM ligne_intervention WHERE piece_id = :id)
            """, nativeQuery = true)
    long nombreReferencesHistoriques(@Param("id") Long id);

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