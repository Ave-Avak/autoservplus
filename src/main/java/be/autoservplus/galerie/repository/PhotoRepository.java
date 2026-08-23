package be.autoservplus.galerie.repository;

import be.autoservplus.galerie.domain.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * Rang suivant dans une galerie, pour poser l ordre d une photo qu on ajoute.
     *
     * <p>Calcule en base et non en memoire : compter les photos deja chargees donnerait
     * un rang faux des qu une aurait ete supprimee entre-temps.</p>
     */
    @Query("""
            SELECT COALESCE(MAX(p.ordre), -1) + 1 FROM Photo p
            WHERE (:prestation IS NOT NULL AND p.prestation.reference = :prestation)
               OR (:piece IS NOT NULL AND p.piece.reference = :piece)
               OR (:intervention IS NOT NULL AND p.intervention.reference = :intervention)
            """)
    short prochainOrdre(@Param("prestation") UUID prestation,
                        @Param("piece") UUID piece,
                        @Param("intervention") UUID intervention);
}
