package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.StatutCommande;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    /** La commande est adressée par sa référence publique, jamais par sa clé primaire. */
    @Query("""
            SELECT c FROM Commande c
            JOIN FETCH c.membre
            WHERE c.reference = :reference
            """)
    Optional<Commande> findByReference(@Param("reference") UUID reference);

    /**
     * Verrou pessimiste (SELECT FOR UPDATE) : la table commande n'a pas de colonne
     * version, et la course webhook / job d'expiration se résout en sérialisant les
     * deux écritures. Le second lecteur voit le statut déjà changé et sa garde
     * d'entité tranche, au lieu d'écraser silencieusement l'état de l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Commande c WHERE c.id = :id")
    Optional<Commande> verrouillerParId(@Param("id") Long id);

    /**
     * Historique des commandes d'un membre, de la plus récente à la plus ancienne
     * (F32). Le membre est identifié par son courriel, comme partout ailleurs à
     * partir du contexte de sécurité : jamais par un identifiant de requête.
     */
    @Query("""
            SELECT c FROM Commande c
            WHERE c.membre.email = :email
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
     * Lignes de PIECE d'une commande, pièces chargées, triées par id de pièce : l'ordre
     * de verrouillage au décrément est ainsi déterministe (anti-interblocage).
     *
     * <p><b>Ne rend QUE les lignes de pièce.</b> Le {@code JOIN FETCH} est une jointure
     * interne : une ligne de service, dont {@code piece} est nul, en est exclue. C'est
     * voulu : cette requête sert le décrément de stock, qui ne concerne que des pièces,
     * et son tri par identifiant de pièce n'aurait pas de sens autrement. Pour les
     * prestations, voir {@link #lignesServiceDe}. Pour un document qui doit porter chaque
     * ligne (facture, note de crédit, détail de la commande), voir {@link #toutesLesLignesDe}.</p>
     */
    @Query("""
            SELECT l FROM LignePanier l
            JOIN FETCH l.piece p
            WHERE l.commande = :commande
            ORDER BY p.id
            """)
    List<LignePanier> lignesDe(@Param("commande") Commande commande);

    /**
     * Lignes de SERVICE d'une commande (F12-b), prestations chargées.
     *
     * <p>Requête distincte plutôt qu'un élargissement de {@link #lignesDe} : cette
     * dernière trie par identifiant de pièce pour garantir un ordre de verrouillage
     * déterministe au décrément de stock, propriété qu'un {@code LEFT JOIN} mêlant les
     * deux natures ferait perdre pour un besoin qui n'en a pas.</p>
     */
    @Query("""
            SELECT l FROM LignePanier l
            JOIN FETCH l.prestation
            WHERE l.commande = :commande
            ORDER BY l.id
            """)
    List<LignePanier> lignesServiceDe(@Param("commande") Commande commande);

    /**
     * Toutes les lignes d'une commande, pièces et prestations, dans l'ordre d'ajout.
     *
     * <p>C'est la requête des documents : une facture doit décrire chaque bien et chaque
     * service avec son prix unitaire hors TVA et sa base par taux. {@link #lignesDe}, qui ne
     * rend que les pièces, laissait sans ligne ni ventilation la facture d'une commande de
     * prestations. Jointures externes, puisqu'une ligne porte soit une pièce, soit une
     * prestation.</p>
     */
    @Query("""
            SELECT l FROM LignePanier l
            LEFT JOIN FETCH l.piece
            LEFT JOIN FETCH l.prestation
            WHERE l.commande = :commande
            ORDER BY l.id
            """)
    List<LignePanier> toutesLesLignesDe(@Param("commande") Commande commande);

    /**
     * Commandes payées comportant au moins une ligne de service (F12-b).
     *
     * <p>{@code DISTINCT} : une commande de plusieurs prestations remonterait autant de
     * fois qu'elle a de lignes.</p>
     */
    @Query("""
            SELECT DISTINCT c FROM Commande c
            JOIN LignePanier l ON l.commande = c
            WHERE c.statut = be.autoservplus.vente.domain.StatutCommande.PAYEE
              AND l.prestation IS NOT NULL
            ORDER BY c.dateCommande DESC
            """)
    List<Commande> payeesAvecService();

    /**
     * Commandes du back-office sur une période, filtrées par statut et par numéro ou
     * adresse du membre. Le motif vaut {@code %} sans recherche et la liste des statuts
     * les contient tous sans filtre : aucun paramètre nul n'a à être typé par la base.
     */
    @Query(value = """
            SELECT c FROM Commande c
            JOIN FETCH c.membre m
            WHERE c.dateCommande >= :debut AND c.dateCommande < :fin
              AND c.statut IN :statuts
              AND (lower(c.numero) LIKE :motif OR lower(m.email) LIKE :motif)
            ORDER BY c.dateCommande DESC, c.id DESC
            """, countQuery = """
            SELECT count(c) FROM Commande c
            JOIN c.membre m
            WHERE c.dateCommande >= :debut AND c.dateCommande < :fin
              AND c.statut IN :statuts
              AND (lower(c.numero) LIKE :motif OR lower(m.email) LIKE :motif)
            """)
    Page<Commande> rechercherAdmin(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                   @Param("statuts") Collection<StatutCommande> statuts,
                                   @Param("motif") String motif, Pageable pagination);

    /**
     * Une demande de rétractation existe-t-elle pour la commande, quel que soit son statut ?
     * Requête native : le module de rétractation dépend de la vente, l'inverse créerait un
     * cycle entre les deux.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM demande_annulation WHERE commande_id = :commande)",
            nativeQuery = true)
    boolean aUneDemandeDeRetractation(@Param("commande") Long commande);

    /** Le membre a-t-il une commande dans ce statut ? Sert la garde de l'adresse. */
    boolean existsByMembreIdAndStatut(Long membreId, StatutCommande statut);

    /** Commandes d'un membre pour sa fiche du back-office (A12), sans limite de période. */
    @Query("""
            SELECT c FROM Commande c
            WHERE c.membre.reference = :membre
            ORDER BY c.dateCommande DESC, c.id DESC
            """)
    List<Commande> commandesDuMembre(@Param("membre") UUID membre);
}
