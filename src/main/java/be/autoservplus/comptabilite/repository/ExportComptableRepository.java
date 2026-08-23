package be.autoservplus.comptabilite.repository;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.vente.domain.Commande;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Lecture des pieces a exporter pour le comptable (BL-3).
 *
 * <p>Les entites sont chargees <b>avec leur titulaire</b> ({@code JOIN FETCH}) : le CSV
 * porte le nom du client sur chaque ligne, et sans le fetch chaque ligne declencherait
 * sa propre requete. Un export d exercice en produirait des milliers.</p>
 *
 * <p>Ce repository ne rend jamais ses entites au-dela du service, qui les projette en
 * lignes de CSV.</p>
 */
@Repository
public class ExportComptableRepository {

    @PersistenceContext
    private EntityManager em;

    /** Factures emises sur la periode, de la plus ancienne a la plus recente. */
    public List<Facture> facturesEmises(Instant debut, Instant fin) {
        return em.createQuery("""
                        SELECT f FROM Facture f
                        JOIN FETCH f.membre
                        LEFT JOIN FETCH f.commande
                        WHERE f.dateEmission >= :debut AND f.dateEmission < :fin
                        ORDER BY f.dateEmission ASC, f.id ASC
                        """, Facture.class)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
    }

    /**
     * Commandes conclues sur la periode, tous statuts confondus.
     *
     * <p>Les annulees et remboursees sont <b>incluses</b> : le comptable doit voir ce
     * qui a ete annule autant que ce qui a ete vendu, et un export qui masquerait les
     * annulations ne se rapprocherait pas du journal des ventes.</p>
     */
    public List<Commande> commandesConclues(Instant debut, Instant fin) {
        return em.createQuery("""
                        SELECT c FROM Commande c
                        JOIN FETCH c.membre
                        WHERE c.dateCommande >= :debut AND c.dateCommande < :fin
                        ORDER BY c.dateCommande ASC, c.id ASC
                        """, Commande.class)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
    }
}
