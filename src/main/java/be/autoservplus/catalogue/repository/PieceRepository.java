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

    /**
     * Retire du stock au plus la quantité demandée, sans descendre sous zéro, et rend le
     * stock d'avant : un résultat inférieur à la demande dit que le plancher a joué.
     *
     * <p>Seul mécanisme de décompte, pour la vente comme pour l'atelier. Une seule
     * instruction verrouille la ligne, lit le stock à jour et l'écrit. Un verrou posé par
     * une requête JPQL ne suffit pas quand la pièce est déjà chargée dans la transaction,
     * ce qui est le cas des deux appelants (lignes d'une commande, lignes d'une
     * intervention) : JPA rend l'instance déjà gérée sans relire son stock, et deux
     * décomptes simultanés en perdaient un ({@code StockPaiementsConcurrenceIT},
     * {@code StockClotureConcurrenceIT}).</p>
     *
     * <p>En retour, l'instance déjà chargée garde l'ancien stock jusqu'à la fin de la
     * transaction. Aucun appelant ne modifie la pièce après le décompte : s'il le faisait,
     * Hibernate réécrirait l'ancien stock avec le reste de la ligne.</p>
     */
    @Query(value = """
            WITH avant AS (SELECT id, quantite_stock FROM piece WHERE id = :id FOR UPDATE)
            UPDATE piece p
            SET quantite_stock = GREATEST(avant.quantite_stock - :quantite, 0),
                updated_at = now(), updated_by = :auteur
            FROM avant
            WHERE p.id = avant.id
            RETURNING avant.quantite_stock
            """, nativeQuery = true)
    int retirerAuPlus(@Param("id") Long id, @Param("quantite") int quantite,
                      @Param("auteur") String auteur);

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
     * ici : en cas d oubli, le RESTRICT en base refuse quand meme la suppression.
     * Une pièce présente dans la composition d'une prestation compte aussi (V76) : la
     * retirer changerait ce que la prestation consomme, elle se désactive. De même une pièce
     * incluse dans une ligne d'intervention, ou citée par leur journal (V77).
     */
    @Query(value = """
            SELECT (SELECT count(*) FROM ligne_panier WHERE piece_id = :id)
                 + (SELECT count(*) FROM ligne_intervention WHERE piece_id = :id)
                 + (SELECT count(*) FROM composition_prestation WHERE piece_id = :id)
                 + (SELECT count(*) FROM piece_incluse WHERE piece_id = :id)
                 + (SELECT count(*) FROM historique_piece_incluse WHERE piece_id = :id)
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
     * Recherche sur le libellé, la description, la marque, la référence du fabricant et
     * le libellé de la catégorie, sans tenir compte de la casse ni des accents.
     *
     * <p>Le motif arrive en minuscules, encadré de jokers, ceux du terme neutralisés par
     * « ! » ({@code MotifRecherche}). {@code unaccent} est l'extension installée par V62.
     * La requête de comptage est écrite à la main : un {@code JOIN FETCH} n'a pas sa
     * place dans un comptage.</p>
     *
     * <p>Les textes traduits sont cherchés dans les trois langues à la fois, quelle que
     * soit celle de l'écran : un visiteur néerlandophone qui tape « huile » trouve le
     * filtre comme s'il avait tapé « olie ». Une traduction absente vaut {@code NULL} et
     * ne correspond à rien. Le tri suit le libellé affiché, donc la langue de l'écran,
     * avec le français pour un libellé non traduit.</p>
     *
     * @param langue code de la langue de l'écran, pour le tri seulement
     */
    @Query(value = """
            SELECT p FROM Piece p JOIN FETCH p.categorie c
            WHERE p.actif = true
              AND ( cast(function('unaccent', LOWER(p.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.description)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.marque)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.referenceFabricant)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!')
            ORDER BY CASE WHEN :langue = 'nl' THEN COALESCE(p.traductions.libelleNl, p.libelle)
                          WHEN :langue = 'en' THEN COALESCE(p.traductions.libelleEn, p.libelle)
                          ELSE p.libelle END, p.id
            """,
            countQuery = """
            SELECT COUNT(p) FROM Piece p JOIN p.categorie c
            WHERE p.actif = true
              AND ( cast(function('unaccent', LOWER(p.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.description)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.traductions.descriptionEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.marque)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(p.referenceFabricant)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelle)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleNl)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!'
                 OR cast(function('unaccent', LOWER(c.libelleEn)) as String) LIKE cast(function('unaccent', :motif) as String) ESCAPE '!')
            """)
    Page<Piece> rechercher(@Param("motif") String motif, @Param("langue") String langue,
                           Pageable pagination);
}