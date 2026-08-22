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

    /** Unicite du nom de service (A1), verifiee sans tenir compte de la casse. */
    boolean existsByLibelleIgnoreCase(String libelle);

    /** Variante pour la modification (A2) : la prestation editee ne se bloque pas elle-meme. */
    boolean existsByLibelleIgnoreCaseAndReferenceNot(String libelle, UUID reference);

    /** Catalogue complet pour le back-office, actifs et inactifs confondus. */
    @Query("SELECT p FROM Prestation p JOIN FETCH p.categorie ORDER BY p.libelle")
    List<Prestation> catalogueComplet();

    /**
     * Nombre de lignes d historique referencant la prestation (RM-29) : reservations
     * ({@code rdv_service}), lignes de panier ou de commande et lignes d intervention.
     * Requete native, miroir applicatif exact des FK {@code ON DELETE RESTRICT}
     * ({@code fk_rdv_service_svc}, {@code fk_ligne_panier_service},
     * {@code fk_ligne_interv_service}) sans dependance inverse vers les autres
     * modules. La table {@code photo}, en CASCADE, ne bloque pas. Toute nouvelle
     * table referencant {@code service} doit s ajouter ici — en cas d oubli, le
     * RESTRICT en base refuse quand meme la suppression.
     */
    @Query(value = """
            SELECT (SELECT count(*) FROM rdv_service WHERE service_id = :id)
                 + (SELECT count(*) FROM ligne_panier WHERE service_id = :id)
                 + (SELECT count(*) FROM ligne_intervention WHERE service_id = :id)
            """, nativeQuery = true)
    long nombreReferencesHistoriques(@Param("id") Long id);

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