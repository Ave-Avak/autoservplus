package be.autoservplus.rgpd.repository;

import be.autoservplus.galerie.domain.Photo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Lecture des photos d'intervention d'un membre pour l'export (F22).
 *
 * <p>Par identifiants d'intervention, ceux que le service vient de lire sur le parc du
 * membre, véhicules retirés compris : même raison que
 * {@link InterventionExportRepository}, une jointure vers {@code Vehicule} ferait
 * disparaître les photos d'un véhicule retiré du parc.</p>
 */
public interface PhotoExportRepository extends Repository<Photo, Long> {

    /** L'appelant garantit une collection d'identifiants non vide. */
    @Query("""
            SELECT p FROM Photo p
            WHERE p.intervention.id IN :interventions
            ORDER BY p.ordre ASC, p.id ASC
            """)
    List<Photo> pourInterventions(@Param("interventions") Collection<Long> interventions);
}
