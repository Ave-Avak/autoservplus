package be.autoservplus.rgpd.repository;

import be.autoservplus.messagerie.domain.Conversation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Fils de discussion du membre et leurs messages, pour l'export (F22). {@code DISTINCT} :
 * la jointure sur les messages répète le fil autant de fois qu'il en porte. Les messages
 * arrivent dans l'ordre d'envoi ({@code @OrderBy} de la relation).
 */
public interface ConversationExportRepository extends Repository<Conversation, Long> {

    @Query("""
            SELECT DISTINCT c FROM Conversation c
            LEFT JOIN FETCH c.messages
            LEFT JOIN FETCH c.intervention
            WHERE c.membre.email = :email
            ORDER BY c.id
            """)
    List<Conversation> pourMembre(@Param("email") String email);
}
