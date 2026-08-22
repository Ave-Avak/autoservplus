package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
