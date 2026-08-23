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

    /**
     * Historique des commandes d un membre, de la plus recente a la plus ancienne
     * (F32). Le membre est identifie par son courriel, comme partout ailleurs a
     * partir du contexte de securite — jamais par un identifiant de requete.
     */
    @Query("""
            SELECT c FROM Commande c
            WHERE lower(c.membre.email) = lower(:email)
            ORDER BY c.dateCommande DESC, c.id DESC
            """)
    List<Commande> historiqueDuMembre(@Param("email") String email);

    /** Candidates du job RM-21 : en attente de paiement depuis avant la limite. */
    @Query("""
            SELECT c FROM Commande c
            WHERE c.statut = :statut AND c.dateCommande < :limite
            """)
    List<Commande> parStatutAnterieuresA(@Param("statut") StatutCommande statut,
                                         @Param("limite") Instant limite);

    /**
     * Lignes de PIECE d une commande, pieces chargees, triees par id de piece : l ordre
     * de verrouillage au decrement est ainsi deterministe (anti-interblocage).
     *
     * <p><b>Ne rend QUE les lignes de piece.</b> Le {@code JOIN FETCH} est une jointure
     * interne : une ligne de service, dont {@code piece} est nul, en est exclue. C est
     * voulu — cette requete sert le decrement de stock, qui ne concerne que des pieces,
     * et son tri par identifiant de piece n aurait pas de sens autrement. Pour les
     * prestations, voir {@link #lignesServiceDe}.</p>
     */
    @Query("""
            SELECT l FROM LignePanier l
            JOIN FETCH l.piece p
            WHERE l.commande = :commande
            ORDER BY p.id
            """)
    List<LignePanier> lignesDe(@Param("commande") Commande commande);

    /**
     * Lignes de SERVICE d une commande (F12-b), prestations chargees.
     *
     * <p>Requete distincte plutot qu un elargissement de {@link #lignesDe} : cette
     * derniere trie par identifiant de piece pour garantir un ordre de verrouillage
     * deterministe au decrement de stock, propriete qu un {@code LEFT JOIN} melant les
     * deux natures ferait perdre pour un besoin qui n en a pas.</p>
     */
    @Query("""
            SELECT l FROM LignePanier l
            JOIN FETCH l.prestation
            WHERE l.commande = :commande
            ORDER BY l.id
            """)
    List<LignePanier> lignesServiceDe(@Param("commande") Commande commande);

    /**
     * Commandes payees comportant au moins une ligne de service (F12-b).
     *
     * <p>{@code DISTINCT} : une commande de plusieurs prestations remonterait autant de
     * fois qu elle a de lignes.</p>
     */
    @Query("""
            SELECT DISTINCT c FROM Commande c
            JOIN LignePanier l ON l.commande = c
            WHERE c.statut = be.autoservplus.vente.domain.StatutCommande.PAYEE
              AND l.prestation IS NOT NULL
            ORDER BY c.dateCommande DESC
            """)
    List<Commande> payeesAvecService();
}
