package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.Indisponibilite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndisponibiliteRepository extends JpaRepository<Indisponibilite, Long> {

    Optional<Indisponibilite> findByReference(UUID reference);

    /**
     * Indisponibilites chevauchant la fenetre [debut, fin), poste charge par jointure.
     * Meme semantique que l operateur && de PostgreSQL sur des intervalles semi-ouverts.
     */
    @Query("""
            SELECT i FROM Indisponibilite i LEFT JOIN FETCH i.poste
            WHERE i.debut < :fin AND i.fin > :debut
            ORDER BY i.debut
            """)
    List<Indisponibilite> chevauchant(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT i FROM Indisponibilite i LEFT JOIN FETCH i.poste WHERE i.fin > :apres ORDER BY i.debut")
    List<Indisponibilite> aVenir(@Param("apres") Instant apres);
}