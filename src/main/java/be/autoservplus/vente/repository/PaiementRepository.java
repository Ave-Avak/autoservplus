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

    /** Paiements non aboutis d une commande, a expirer avec elle (job RM-21). */
    List<Paiement> findByCommandeAndStatutIn(Commande commande, Collection<StatutPaiement> statuts);
}
