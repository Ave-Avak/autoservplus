package be.autoservplus.catalogue.repository;

import be.autoservplus.catalogue.domain.Prestation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * Liste du back-office, actives et inactives, par page et par libellé. Une catégorie
     * vide ({@code ''}) les prend toutes : une chaîne vide plutôt que {@code NULL}, dont
     * PostgreSQL ne sait pas toujours déduire le type dans {@code :p IS NULL}. Le motif
     * cherche dans le libellé, sans accents, et dans le code.
     */
    @Query(value = """
            SELECT p FROM Prestation p JOIN FETCH p.categorie c
            WHERE (:categorie = '' OR c.code = :categorie)
              AND p.actif IN :actifs
              AND (cast(function('unaccent', LOWER(p.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                   OR LOWER(p.code) LIKE :motif ESCAPE '!')
            ORDER BY p.libelle, p.id
            """,
            countQuery = """
            SELECT count(p) FROM Prestation p JOIN p.categorie c
            WHERE (:categorie = '' OR c.code = :categorie)
              AND p.actif IN :actifs
              AND (cast(function('unaccent', LOWER(p.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                   OR LOWER(p.code) LIKE :motif ESCAPE '!')
            """)
    Page<Prestation> pourAdmin(@Param("categorie") String categorie,
                               @Param("actifs") Collection<Boolean> actifs,
                               @Param("motif") String motif, Pageable pagination);

    /**
     * Nombre de lignes d historique referencant la prestation (RM-29) : reservations
     * ({@code rdv_service}), lignes de panier ou de commande et lignes d intervention.
     * Requete native, miroir applicatif exact des FK {@code ON DELETE RESTRICT}
     * ({@code fk_rdv_service_svc}, {@code fk_ligne_panier_service},
     * {@code fk_ligne_interv_service}) sans dependance inverse vers les autres
     * modules. La table {@code photo}, en CASCADE, ne bloque pas. Toute nouvelle
     * table referencant {@code service} doit s ajouter ici : en cas d oubli, le
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

    /** Prestations de l'accueil, dans l'ordre choisi par le garage (V80). */
    List<Prestation> findByActifTrueAndOrdreVitrineNotNullOrderByOrdreVitrineAsc();

    @Query("""
            SELECT p FROM Prestation p JOIN FETCH p.categorie c
            WHERE c.code = :codeCategorie AND p.actif = true
            ORDER BY p.libelle
            """)
    List<Prestation> findByCategorieCodeAndActifTrueOrderByLibelleAsc(
            @Param("codeCategorie") String codeCategorie);

    /**
     * Recherche sur le libellé, la description et le libellé de la catégorie, sans tenir
     * compte de la casse ni des accents. Mêmes règles que {@code PieceRepository.rechercher}.
     */
    @Query(value = """
            SELECT p FROM Prestation p JOIN FETCH p.categorie c
            WHERE p.actif = true
              AND ( cast(function('unaccent', LOWER(p.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.description)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!')
            ORDER BY CASE WHEN :langue = 'nl' THEN COALESCE(p.traductions.libelleNl, p.libelle)
                          WHEN :langue = 'en' THEN COALESCE(p.traductions.libelleEn, p.libelle)
                          ELSE p.libelle END, p.id
            """,
            countQuery = """
            SELECT COUNT(p) FROM Prestation p JOIN p.categorie c
            WHERE p.actif = true
              AND ( cast(function('unaccent', LOWER(p.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.description)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!')
            """)
    Page<Prestation> rechercher(@Param("motif") String motif, @Param("langue") String langue,
                           Pageable pagination);
}