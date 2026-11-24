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

    /**
     * Interventions d'un véhicule, pour son historique d'entretien (F21).
     *
     * <p>Filtre sur {@code i.vehicule.id}, que Hibernate lit dans la clé étrangère sans
     * joindre {@code Vehicule} : la restriction de suppression logique du véhicule
     * reste hors du chemin, et un véhicule retiré du parc garde son historique, comme
     * dans l'export F22. Le tri se fait sur {@link Intervention#dateDeReference()},
     * calculée, donc par l'appelant.</p>
     */
    @Query("""
            SELECT DISTINCT i FROM Intervention i
            LEFT JOIN FETCH i.rdv
            LEFT JOIN FETCH i.lignes
            WHERE i.vehicule.id = :vehiculeId
            """)
    List<Intervention> historiqueDuVehicule(@Param("vehiculeId") Long vehiculeId);

    /**
     * Parmi ces pièces, celles à commander : leur stock est inférieur à la quantité que
     * réclament les interventions ouvertes (Q-H-30). Sont comptées les lignes non refusées
     * des interventions ni terminées ni annulées, celles qui attendent la réponse du membre
     * (RM-15) comprises. Comparer chaque ligne seule au stock laisserait deux dossiers qui
     * demandent la même pièce unique se croire servis l'un et l'autre.
     */
    @Query("""
            SELECT l.piece.id FROM LigneIntervention l
            WHERE l.piece.id IN :pieces
              AND l.intervention.statut NOT IN :closes
              AND (l.accordMembre IS NULL OR l.accordMembre = true)
            GROUP BY l.piece.id, l.piece.quantiteStock
            HAVING l.piece.quantiteStock < SUM(l.quantite)
            """)
    List<Long> piecesACommander(@Param("pieces") Collection<Long> pieces,
                                @Param("closes") Collection<StatutIntervention> closes);
}
