package be.autoservplus.intervention.repository;

import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    @Query("""
            SELECT DISTINCT i FROM Intervention i
            LEFT JOIN FETCH i.rdv JOIN FETCH i.vehicule
            LEFT JOIN FETCH i.lignes l LEFT JOIN FETCH l.prestation LEFT JOIN FETCH l.piece
            WHERE i.reference = :reference
            """)
    Optional<Intervention> findByReference(@Param("reference") UUID reference);

    /** Sert a l idempotence de la creation depuis un RDV (un seul RDV -> une intervention). */
    Optional<Intervention> findByRdvId(Long rdvId);

    /** Seconde origine (F12-b) : garantit l idempotence de la creation par commande. */
    Optional<Intervention> findByCommandeId(Long commandeId);

    boolean existsByRdvId(Long rdvId);

    @Query("""
            SELECT i FROM Intervention i
            LEFT JOIN FETCH i.rdv r JOIN FETCH r.membre
            WHERE r.reference = :rdvReference
            """)
    Optional<Intervention> findByRdvReference(@Param("rdvReference") UUID rdvReference);

    @Query("""
            SELECT DISTINCT i FROM Intervention i
            LEFT JOIN FETCH i.rdv JOIN FETCH i.vehicule
            WHERE i.statut IN :statuts
            ORDER BY i.createdAt DESC
            """)
    List<Intervention> findByStatutIn(@Param("statuts") Collection<StatutIntervention> statuts);
}
