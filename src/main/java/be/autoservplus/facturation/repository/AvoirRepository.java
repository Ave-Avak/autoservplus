package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AvoirRepository extends JpaRepository<Avoir, Long> {

    /**
     * Avoir d une facture, s il en existe un. Support de l idempotence : le controle
     * applicatif s appuie dessus, l index unique {@code uq_avoir_facture} (V27)
     * tranche les courses que ce controle ne peut pas voir.
     */
    Optional<Avoir> findByFacture(Facture facture);

    /** L avoir est adresse par sa reference publique, jamais par sa cle primaire. */
    @Query("""
            SELECT a FROM Avoir a
            JOIN FETCH a.facture f
            JOIN FETCH f.membre
            LEFT JOIN FETCH f.commande
            WHERE a.reference = :reference
            """)
    Optional<Avoir> findByReference(@Param("reference") UUID reference);
}
