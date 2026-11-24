package be.autoservplus.rgpd.repository;

import be.autoservplus.devis.domain.Devis;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des devis d'un membre pour l'export (F22), lignes comprises, dans l'ordre
 * chronologique comme les autres sections. {@code DISTINCT} : la jointure sur les lignes
 * répète le devis autant de fois qu'il en a.
 */
public interface DevisExportRepository extends Repository<Devis, Long> {

    @Query("""
            SELECT DISTINCT d FROM Devis d
            LEFT JOIN FETCH d.lignes
            WHERE d.membre.email = :email
            ORDER BY d.dateEmission, d.id
            """)
    List<Devis> pourMembre(@Param("email") String email);
}
