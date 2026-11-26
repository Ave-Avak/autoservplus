package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvoirRepository extends JpaRepository<Avoir, Long> {

    /**
     * Avoir d'une facture, s'il en existe un. Support de l'idempotence : le contrôle
     * applicatif s'appuie dessus, l'index unique {@code uq_avoir_facture} (V27)
     * tranche les courses que ce contrôle ne peut pas voir.
     */
    Optional<Avoir> findByFacture(Facture facture);

    /** L'avoir est adressé par sa référence publique, jamais par sa clé primaire. */
    @Query("""
            SELECT a FROM Avoir a
            JOIN FETCH a.facture f
            JOIN FETCH f.membre
            LEFT JOIN FETCH f.commande
            WHERE a.reference = :reference
            """)
    Optional<Avoir> findByReference(@Param("reference") UUID reference);

    /** Notes de crédit d'un membre, la plus récente d'abord, pour son espace documents. */
    @Query("""
            SELECT a FROM Avoir a
            JOIN FETCH a.facture f
            WHERE f.membre.email = :email
            ORDER BY a.dateEmission DESC, a.id DESC
            """)
    List<Avoir> avoirsDuMembre(@Param("email") String email);

    /** Avoirs du back-office sur une période, par numéro d'avoir, de facture ou d'intervention, ou adresse. */
    @Query(value = """
            SELECT a FROM Avoir a
            JOIN FETCH a.facture f
            JOIN FETCH f.membre m
            LEFT JOIN FETCH f.commande c
            LEFT JOIN FETCH f.intervention i
            WHERE a.dateEmission >= :debut AND a.dateEmission < :fin
              AND (lower(a.numero) LIKE :motif OR lower(f.numero) LIKE :motif
                   OR lower(m.email) LIKE :motif OR lower(i.numero) LIKE :motif)
            ORDER BY a.dateEmission DESC, a.id DESC
            """, countQuery = """
            SELECT count(a) FROM Avoir a
            JOIN a.facture f
            JOIN f.membre m
            LEFT JOIN f.intervention i
            WHERE a.dateEmission >= :debut AND a.dateEmission < :fin
              AND (lower(a.numero) LIKE :motif OR lower(f.numero) LIKE :motif
                   OR lower(m.email) LIKE :motif OR lower(i.numero) LIKE :motif)
            """)
    Page<Avoir> rechercherAdmin(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                @Param("motif") String motif, Pageable pagination);
}
