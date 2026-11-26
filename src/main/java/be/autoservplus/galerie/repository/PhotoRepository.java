package be.autoservplus.galerie.repository;

import be.autoservplus.galerie.domain.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /** Photos d une prestation, dans l ordre voulu par le garage. */
    @Query("""
            SELECT p FROM Photo p
            WHERE p.prestation.reference = :reference
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Photo> dePrestation(@Param("reference") UUID reference);

    @Query("""
            SELECT p FROM Photo p
            WHERE p.piece.reference = :reference
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Photo> dePiece(@Param("reference") UUID reference);

    @Query("""
            SELECT p FROM Photo p
            WHERE p.intervention.reference = :reference
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Photo> dIntervention(@Param("reference") UUID reference);

    /** Visuel de la catégorie d'une pièce (V79), montré quand la pièce n'a pas de photo propre. */
    @Query("""
            SELECT p FROM Photo p
            WHERE p.categorie = (SELECT pc.categorie FROM Piece pc WHERE pc.reference = :reference)
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Photo> deLaCategorieDeLaPiece(@Param("reference") UUID reference);

    /**
     * Photos propres des prestations d'une liste, avec la référence de leur prestation, pour
     * la vignette de chaque carte en une requête.
     */
    @Query("""
            SELECT s.reference, p FROM Photo p JOIN p.prestation s
            WHERE s.reference IN :references
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Object[]> desPrestations(@Param("references") Collection<UUID> references);

    /** Photos propres des pièces d'une liste, avec la référence de leur pièce. */
    @Query("""
            SELECT pc.reference, p FROM Photo p JOIN p.piece pc
            WHERE pc.reference IN :references
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Object[]> desPieces(@Param("references") Collection<UUID> references);

    /**
     * Photo d'une prestation, d'une pièce ou d'une catégorie, seule servie à l'adresse publique. Une photo
     * d'intervention n'y est pas trouvée, comme une référence inconnue.
     */
    @Query("""
            SELECT p FROM Photo p
            WHERE p.reference = :reference AND p.intervention IS NULL
            """)
    Optional<Photo> duCatalogue(@Param("reference") UUID reference);

    /** Photo de cette intervention-là : une référence prise dans une autre n'est pas trouvée. */
    @Query("""
            SELECT p FROM Photo p
            WHERE p.reference = :photo AND p.intervention.reference = :intervention
            """)
    Optional<Photo> dUneIntervention(@Param("intervention") UUID intervention,
                                     @Param("photo") UUID photo);

    /** Photo d'une fiche donnée, quelle que soit sa nature, pour la retirer de sa galerie. */
    @Query("""
            SELECT p FROM Photo p
            LEFT JOIN p.prestation s LEFT JOIN p.piece pc LEFT JOIN p.intervention i
            WHERE p.reference = :photo
              AND (s.reference = :fiche OR pc.reference = :fiche OR i.reference = :fiche)
            """)
    Optional<Photo> deLaFiche(@Param("fiche") UUID fiche, @Param("photo") UUID photo);

    /**
     * Rang suivant dans une galerie, pour poser l ordre d une photo qu on ajoute.
     *
     * <p>Calcule en base et non en memoire : compter les photos deja chargees donnerait
     * un rang faux des qu une aurait ete supprimee entre-temps.</p>
     *
     * <p>Jointures externes explicites : un chemin {@code p.prestation.reference} écrit
     * dans le WHERE est une jointure interne, et les trois ensemble ne retenaient
     * aucune photo, qui n'a jamais qu'une seule origine. Le rang repartait alors de 0 à
     * chaque dépôt.</p>
     */
    @Query("""
            SELECT COALESCE(MAX(p.ordre), -1) + 1 FROM Photo p
            LEFT JOIN p.prestation s LEFT JOIN p.piece pc LEFT JOIN p.intervention i
            WHERE (:prestation IS NOT NULL AND s.reference = :prestation)
               OR (:piece IS NOT NULL AND pc.reference = :piece)
               OR (:intervention IS NOT NULL AND i.reference = :intervention)
            """)
    short prochainOrdre(@Param("prestation") UUID prestation,
                        @Param("piece") UUID piece,
                        @Param("intervention") UUID intervention);
}
