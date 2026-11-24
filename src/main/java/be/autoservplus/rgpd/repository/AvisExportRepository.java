package be.autoservplus.rgpd.repository;

import be.autoservplus.avis.domain.Avis;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Avis déposés par le membre pour l'export (F22), publiés ou non. L'intervention est jointe
 * pour son numéro, qui situe l'avis.
 */
public interface AvisExportRepository extends Repository<Avis, Long> {

    @Query("""
            SELECT a FROM Avis a
            LEFT JOIN FETCH a.intervention
            WHERE a.membre.email = :email
            ORDER BY a.dateDepot, a.id
            """)
    List<Avis> pourMembre(@Param("email") String email);
}
