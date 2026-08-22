package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.vente.domain.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactureRepository extends JpaRepository<Facture, Long> {

    /**
     * Facture d une commande, s il y en a une. Support de l idempotence : le
     * controle applicatif s appuie dessus, l index partiel {@code uq_facture_commande}
     * tranche les courses que ce controle ne peut pas voir.
     */
    Optional<Facture> findByCommande(Commande commande);

    /** La facture est adressee par sa reference publique, jamais par sa cle primaire. */
    @Query("""
            SELECT f FROM Facture f
            JOIN FETCH f.membre
            LEFT JOIN FETCH f.commande
            WHERE f.reference = :reference
            """)
    Optional<Facture> findByReference(@Param("reference") UUID reference);

    /**
     * Factures des commandes d un membre, de la plus recente a la plus ancienne.
     * Le membre est identifie par son courriel, comme partout ailleurs a partir du
     * contexte de securite.
     */
    @Query("""
            SELECT f FROM Facture f
            JOIN FETCH f.commande c
            WHERE lower(f.membre.email) = lower(:email)
            ORDER BY f.dateEmission DESC, f.id DESC
            """)
    List<Facture> facturesDuMembre(@Param("email") String email);
}
