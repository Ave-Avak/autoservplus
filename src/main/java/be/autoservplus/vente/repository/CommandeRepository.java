package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.StatutCommande;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    /** La commande est adressee par sa reference publique, jamais par sa cle primaire. */
    @Query("""
            SELECT c FROM Commande c
            JOIN FETCH c.membre
            WHERE c.reference = :reference
            """)
    Optional<Commande> findByReference(@Param("reference") UUID reference);

    /**
     * Verrou pessimiste (SELECT FOR UPDATE) : la table commande n a pas de colonne
     * version, et la course webhook / job d expiration se resout en serialisant les
     * deux ecritures — le second lecteur voit le statut deja change et sa garde
     * d entite tranche, au lieu d ecraser silencieusement l etat de l autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Commande c WHERE c.id = :id")
    Optional<Commande> verrouillerParId(@Param("id") Long id);

    /** Candidates du job RM-21 : en attente de paiement depuis avant la limite. */
    @Query("""
            SELECT c FROM Commande c
            WHERE c.statut = :statut AND c.dateCommande < :limite
            """)
    List<Commande> parStatutAnterieuresA(@Param("statut") StatutCommande statut,
                                         @Param("limite") Instant limite);

    /**
     * Lignes d une commande, pieces chargees, triees par id de piece : l ordre de
     * verrouillage des pieces au decrement est ainsi deterministe (anti-interblocage).
     */
    @Query("""
            SELECT l FROM LignePanier l
            JOIN FETCH l.piece p
            WHERE l.commande = :commande
            ORDER BY p.id
            """)
    List<LignePanier> lignesDe(@Param("commande") Commande commande);
}
