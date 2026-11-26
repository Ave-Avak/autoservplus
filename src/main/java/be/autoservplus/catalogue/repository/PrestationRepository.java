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
            """ + MOTS + """
            ORDER BY CASE WHEN :langue = 'nl' THEN COALESCE(p.traductions.libelleNl, p.libelle)
                          WHEN :langue = 'en' THEN COALESCE(p.traductions.libelleEn, p.libelle)
                          ELSE p.libelle END, p.id
            """,
            countQuery = """
            SELECT COUNT(p) FROM Prestation p JOIN p.categorie c
            WHERE p.actif = true
            """ + MOTS)
    Page<Prestation> rechercherParMots(@Param("mot1") String mot1, @Param("mot2") String mot2,
                                   @Param("mot3") String mot3, @Param("mot4") String mot4,
                                   @Param("mot5") String mot5, @Param("langue") String langue,
                                   Pageable pagination);

    /** {@link #rechercherParMots} avec les cinq motifs de {@code MotifRecherche.mots}. */
    default Page<Prestation> rechercher(List<String> motifs, String langue, Pageable pagination) {
        return rechercherParMots(motifs.get(0), motifs.get(1), motifs.get(2), motifs.get(3),
                motifs.get(4), langue, pagination);
    }

    /** Tout le texte cherché d'un article, en minuscules, champs séparés par une espace. */
    String TEXTE_CHERCHE = "cast(function('unaccent', LOWER(cast(function('concat_ws', ' ', "
            + "p.libelle, p.traductions.libelleNl, p.traductions.libelleEn, "
            + "p.description, p.traductions.descriptionNl, p.traductions.descriptionEn, "
            + "c.libelle, c.libelleNl, c.libelleEn) as String))) as String)";

    /** Chaque mot présent dans le texte cherché, comme {@code PieceRepository.MOTS}. */
    String MOTS = ""
            + "  AND " + TEXTE_CHERCHE + " LIKE cast(function('unaccent', :mot1) as String) ESCAPE '!'\n"
            + "  AND " + TEXTE_CHERCHE + " LIKE cast(function('unaccent', :mot2) as String) ESCAPE '!'\n"
            + "  AND " + TEXTE_CHERCHE + " LIKE cast(function('unaccent', :mot3) as String) ESCAPE '!'\n"
            + "  AND " + TEXTE_CHERCHE + " LIKE cast(function('unaccent', :mot4) as String) ESCAPE '!'\n"
            + "  AND " + TEXTE_CHERCHE + " LIKE cast(function('unaccent', :mot5) as String) ESCAPE '!'\n";

}