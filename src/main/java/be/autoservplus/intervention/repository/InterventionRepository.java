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

public interface InterventionRepository extends JpaRepository<Intervention, Long>, VerrouIntervention {

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
     * réclament les travaux à venir (Q-H-30). Trois sources, toutes non refusées :
     * les lignes de pièce des interventions ni terminées ni annulées, celles qui attendent la
     * réponse du membre (RM-15) comprises, les pièces incluses des lignes de forfait de ces
     * mêmes interventions (V77), et la composition des forfaits des rendez-vous en attente ou
     * confirmés qui n'ont pas encore d'intervention, sauf pose seule. Comparer chaque source
     * seule au stock laisserait deux dossiers qui demandent la même pièce unique se croire
     * servis l'un et l'autre.
     *
     * <p>SQL natif : les trois sources n'ont pas de racine commune, et le cumul ne se calcule
     * qu'après leur union.</p>
     */
    @Query(nativeQuery = true, value = """
            SELECT d.piece_id FROM (
                SELECT l.piece_id, l.quantite
                FROM ligne_intervention l JOIN intervention i ON i.id = l.intervention_id
                WHERE l.piece_id IS NOT NULL AND i.deleted_at IS NULL
                  AND i.statut NOT IN ('TERMINEE', 'ANNULEE')
                  AND (l.accord_membre IS NULL OR l.accord_membre)
                UNION ALL
                SELECT pi.piece_id, pi.quantite
                FROM piece_incluse pi
                JOIN ligne_intervention l ON l.id = pi.ligne_intervention_id
                JOIN intervention i ON i.id = l.intervention_id
                WHERE i.deleted_at IS NULL AND i.statut NOT IN ('TERMINEE', 'ANNULEE')
                  AND (l.accord_membre IS NULL OR l.accord_membre)
                UNION ALL
                SELECT c.piece_id, c.quantite * rs.quantite
                FROM rdv_service rs
                JOIN rdv r ON r.id = rs.rdv_id
                JOIN composition_prestation c ON c.service_id = rs.service_id
                WHERE r.deleted_at IS NULL AND r.statut IN ('EN_ATTENTE', 'CONFIRME')
                  AND (rs.pieces_client IS NULL OR NOT rs.pieces_client)
                  AND NOT EXISTS (SELECT 1 FROM intervention i
                                  WHERE i.rdv_id = r.id AND i.deleted_at IS NULL)
            ) d
            JOIN piece p ON p.id = d.piece_id
            WHERE d.piece_id IN (:pieces)
            GROUP BY d.piece_id, p.quantite_stock
            HAVING p.quantite_stock < SUM(d.quantite)
            """)
    List<Long> piecesACommander(@Param("pieces") Collection<Long> pieces);

    /**
     * Interventions dont le membre est titulaire, avec la plaque, la marque et le modèle
     * du véhicule ({@code [id, plaque, marque, modele]}). Le titulaire suit
     * {@link be.autoservplus.intervention.domain.Intervention#titulaire()} : membre du
     * rendez-vous, sinon de la commande, sinon propriétaire du véhicule. La jointure SQL
     * sur {@code vehicule} ignore sa suppression logique : un véhicule retiré du parc
     * garde ses dossiers dans l'espace documents, comme dans son historique.
     */
    @Query(nativeQuery = true, value = """
            SELECT i.id, v.plaque, v.marque, v.modele
            FROM intervention i
            JOIN vehicule v ON v.id = i.vehicule_id
            LEFT JOIN rdv r ON r.id = i.rdv_id
            LEFT JOIN commande c ON c.id = i.commande_id
            JOIN utilisateur u ON u.id = COALESCE(r.membre_id, c.membre_id, v.membre_id)
            WHERE u.email = :email AND u.anonymise_le IS NULL AND i.deleted_at IS NULL
            """)
    List<Object[]> dossiersDuTitulaire(@Param("email") String email);
}
