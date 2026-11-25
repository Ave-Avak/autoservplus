package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.Encaissement;
import be.autoservplus.facturation.domain.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EncaissementRepository extends JpaRepository<Encaissement, Long> {

    /** Encaissement non annulé d'une facture : au plus un, {@code uq_encaissement_facture_actif}. */
    Optional<Encaissement> findByFactureAndAnnuleLeIsNull(Facture facture);

    /** Encaissements de ces factures, annulés compris, dans l'ordre de leur enregistrement (F22). */
    @Query("""
            SELECT e FROM Encaissement e
            JOIN FETCH e.facture f
            WHERE f IN :factures
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<Encaissement> deFactures(@Param("factures") Collection<Facture> factures);
}
