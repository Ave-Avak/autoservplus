package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    /**
     * Resolution d une notification entrante : le prestataire n envoie que sa
     * reference, la commande est chargee avec pour la suite du traitement.
     */
    @Query("""
            SELECT p FROM Paiement p
            JOIN FETCH p.commande c JOIN FETCH c.membre
            WHERE p.referenceMollie = :referencePrestataire
            """)
    Optional<Paiement> findByReferenceMollie(@Param("referencePrestataire") String referencePrestataire);

    /**
     * Derniere tentative de paiement d une commande, pour la reconciliation au retour
     * du membre. L identifiant departage deux tentatives nees dans la meme
     * milliseconde : cas rare en production, systematique avec une horloge figee de
     * test, et un depart au hasard ferait relire le statut de la mauvaise.
     */
    Optional<Paiement> findFirstByCommandeOrderByDateInitiationDescIdDesc(Commande commande);

    /** Paiement arrivé après l'annulation de la commande (V64), s'il y en a un. */
    Optional<Paiement> findFirstByCommandeAndRecuApresAnnulationLeIsNotNull(Commande commande);

    /** Par référence interne, commande et membre chargés pour les avis et l'écran. */
    @Query("""
            SELECT p FROM Paiement p
            JOIN FETCH p.commande c JOIN FETCH c.membre
            WHERE p.reference = :reference
            """)
    Optional<Paiement> findByReference(@Param("reference") UUID reference);

    /**
     * Verrouille le paiement pour son remboursement. Deux demandes simultanées, double
     * clic ou deux administrateurs, se suivent au lieu de se croiser : la seconde relit
     * le paiement une fois la première validée, et le trouve déjà remboursé.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Paiement p WHERE p.reference = :reference")
    Optional<Paiement> verrouillerParReference(@Param("reference") UUID reference);

    /**
     * Paiements encaissés sur une commande déjà annulée (V64) : ceux qui attendent leur
     * remboursement d'abord, puis du plus récent au plus ancien.
     */
    @Query("""
            SELECT p FROM Paiement p
            JOIN FETCH p.commande c JOIN FETCH c.membre
            WHERE p.recuApresAnnulationLe IS NOT NULL
            ORDER BY CASE WHEN p.rembourseLe IS NULL THEN 0 ELSE 1 END,
                     p.recuApresAnnulationLe DESC, p.id DESC
            """)
    List<Paiement> recusApresAnnulation();

    /** Paiements non aboutis d une commande, a expirer avec elle (job RM-21). */
    List<Paiement> findByCommandeAndStatutIn(Commande commande, Collection<StatutPaiement> statuts);
}
