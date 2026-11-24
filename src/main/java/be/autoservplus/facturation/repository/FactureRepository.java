package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.vente.domain.Commande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactureRepository extends JpaRepository<Facture, Long> {

    /**
     * Facture d'une commande, s'il y en a une. Support de l'idempotence : le
     * contrôle applicatif s'appuie dessus, l'index partiel {@code uq_facture_commande}
     * tranche les courses que ce contrôle ne peut pas voir.
     */
    Optional<Facture> findByCommande(Commande commande);

    /** Facture d'atelier d'une intervention (A15, RM-17) : au plus une, {@code uq_facture_intervention}. */
    Optional<Facture> findByIntervention(Intervention intervention);

    /**
     * Intervention d'une facture d'atelier, lignes comprises. La facture arrive souvent
     * détachée de la requête qui l'a chargée (téléchargement du PDF) : son association
     * paresseuse ne se charge plus, la requête la relit dans la transaction en cours.
     */
    @Query("""
            SELECT i FROM Facture f
            JOIN f.intervention i
            LEFT JOIN FETCH i.lignes
            WHERE f.id = :facture
            """)
    Optional<Intervention> interventionFacturee(@Param("facture") Long facture);

    /** La facture est adressée par sa référence publique, jamais par sa clé primaire. */
    @Query("""
            SELECT f FROM Facture f
            JOIN FETCH f.membre
            LEFT JOIN FETCH f.commande
            WHERE f.reference = :reference
            """)
    Optional<Facture> findByReference(@Param("reference") UUID reference);

    /**
     * Factures des commandes d'un membre, de la plus récente à la plus ancienne.
     * Le membre est identifié par son courriel, comme partout ailleurs à partir du
     * contexte de sécurité.
     */
    @Query("""
            SELECT f FROM Facture f
            JOIN FETCH f.commande c
            WHERE f.membre.email = :email
            ORDER BY f.dateEmission DESC, f.id DESC
            """)
    List<Facture> facturesDuMembre(@Param("email") String email);

    /**
     * Compte sans charger : sert à refuser qu'un membre vide son adresse postale alors
     * qu'une facture reste à générer. Charger la liste pour n'en regarder que la
     * taille coûterait les lignes et leurs relations.
     */
    long countByMembreEmail(String email);

    /** Factures du back-office sur une période, par numéro, adresse du membre, commande ou intervention. */
    @Query(value = """
            SELECT f FROM Facture f
            JOIN FETCH f.membre m
            LEFT JOIN FETCH f.commande c
            LEFT JOIN FETCH f.intervention i
            WHERE f.dateEmission >= :debut AND f.dateEmission < :fin
              AND (lower(f.numero) LIKE :motif OR lower(m.email) LIKE :motif
                   OR lower(c.numero) LIKE :motif OR lower(i.numero) LIKE :motif)
            ORDER BY f.dateEmission DESC, f.id DESC
            """, countQuery = """
            SELECT count(f) FROM Facture f
            JOIN f.membre m
            LEFT JOIN f.commande c
            LEFT JOIN f.intervention i
            WHERE f.dateEmission >= :debut AND f.dateEmission < :fin
              AND (lower(f.numero) LIKE :motif OR lower(m.email) LIKE :motif
                   OR lower(c.numero) LIKE :motif OR lower(i.numero) LIKE :motif)
            """)
    Page<Facture> rechercherAdmin(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                  @Param("motif") String motif, Pageable pagination);
}
