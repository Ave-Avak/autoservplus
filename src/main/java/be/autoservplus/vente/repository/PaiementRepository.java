package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
     * milliseconde — cas rare en production, systematique avec une horloge figee de
     * test, et un depart au hasard ferait relire le statut de la mauvaise.
     */
    Optional<Paiement> findFirstByCommandeOrderByDateInitiationDescIdDesc(Commande commande);

    /** Paiements non aboutis d une commande, a expirer avec elle (job RM-21). */
    List<Paiement> findByCommandeAndStatutIn(Commande commande, Collection<StatutPaiement> statuts);
}
