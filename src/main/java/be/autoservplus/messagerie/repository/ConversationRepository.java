package be.autoservplus.messagerie.repository;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.messagerie.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Fil adresse par sa reference publique, messages charges.
     *
     * <p>{@code LEFT JOIN FETCH} et non {@code JOIN FETCH} : un fil vient d etre ouvert
     * sans encore porter de message dans la meme requete, et un {@code JOIN} l aurait
     * fait disparaitre du resultat.</p>
     */
    @Query("""
            SELECT DISTINCT c FROM Conversation c
            JOIN FETCH c.membre
            LEFT JOIN FETCH c.messages m
            WHERE c.reference = :reference
            """)
    Optional<Conversation> findByReferenceAvecMessages(@Param("reference") UUID reference);

    Optional<Conversation> findByReference(UUID reference);

    /** Fils du membre, le plus recemment remue d abord. */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.membre = :membre
            ORDER BY c.updatedAt DESC, c.id DESC
            """)
    List<Conversation> duMembre(@Param("membre") Utilisateur membre);

    /** Tous les fils, pour le garage. Les fils ouverts remontent avant les clos. */
    @Query("""
            SELECT c FROM Conversation c
            JOIN FETCH c.membre
            ORDER BY c.cloturee ASC, c.updatedAt DESC, c.id DESC
            """)
    List<Conversation> tousPourLeGarage();

    /**
     * Nombre de fils du membre comportant au moins un message non lu venu du garage.
     *
     * <p>Compte des <b>fils</b> et non des messages : le compteur de navigation dit
     * combien de conversations reclament une lecture, pas combien de phrases y ont ete
     * ecrites.</p>
     */
    @Query("""
            SELECT COUNT(DISTINCT c) FROM Conversation c
            JOIN c.messages m
            WHERE c.membre = :membre
              AND m.lu = false
              AND m.role = be.autoservplus.messagerie.domain.RoleExpediteur.ADMINISTRATEUR
            """)
    long nombreFilsNonLusParLeMembre(@Param("membre") Utilisateur membre);
}
