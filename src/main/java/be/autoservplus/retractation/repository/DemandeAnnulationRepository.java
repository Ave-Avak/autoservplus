package be.autoservplus.retractation.repository;

import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.vente.domain.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeAnnulationRepository extends JpaRepository<DemandeAnnulation, Long> {

    /**
     * Une demande pendante existe-t-elle deja sur cette commande ? Controle applicatif
     * de l idempotence ; l index partiel {@code uq_demande_annulation_en_attente}
     * tranche la course entre deux soumissions simultanees.
     */
    boolean existsByCommandeAndStatut(Commande commande, StatutDemandeAnnulation statut);

    /**
     * Toutes les demandes d une commande, de la plus recente a la plus ancienne. Le
     * membre peut en avoir plusieurs : un refus n eteint pas son droit, il peut
     * redemander. L ecran ne montre que la derniere, l historique reste consultable.
     */
    @Query("""
            SELECT d FROM DemandeAnnulation d
            WHERE d.commande = :commande
            ORDER BY d.dateDemande DESC, d.id DESC
            """)
    List<DemandeAnnulation> historiqueDe(@Param("commande") Commande commande);

    /**
     * File de traitement du garage : les demandes non tranchees, de la plus ancienne
     * a la plus recente. Le delai legal de remboursement court a partir de la demande
     * (14 jours, CDE art. VI.50) — c est donc la plus vieille qui presse, pas la
     * derniere arrivee.
     */
    @Query("""
            SELECT d FROM DemandeAnnulation d
            JOIN FETCH d.commande c
            JOIN FETCH c.membre
            WHERE d.statut = be.autoservplus.retractation.domain.StatutDemandeAnnulation.EN_ATTENTE
            ORDER BY d.dateDemande ASC, d.id ASC
            """)
    List<DemandeAnnulation> enAttente();

    /**
     * Demande adressee par sa reference publique, avec de quoi trancher sans requete
     * supplementaire : la commande, son membre, et l avoir eventuel.
     */
    @Query("""
            SELECT d FROM DemandeAnnulation d
            JOIN FETCH d.commande c
            JOIN FETCH c.membre
            LEFT JOIN FETCH d.avoir
            WHERE d.reference = :reference
            """)
    Optional<DemandeAnnulation> findByReference(@Param("reference") UUID reference);

    /**
     * Demandes du membre connecte, pour pastiller sa liste de commandes. Le membre
     * est identifie par son courriel, comme partout ailleurs a partir du contexte de
     * securite — jamais par un identifiant de requete.
     */
    @Query("""
            SELECT d FROM DemandeAnnulation d
            JOIN FETCH d.commande c
            LEFT JOIN FETCH d.avoir
            WHERE lower(c.membre.email) = lower(:email)
            ORDER BY d.dateDemande ASC, d.id ASC
            """)
    List<DemandeAnnulation> demandesDuMembre(@Param("email") String email);
}
