package be.autoservplus.rgpd.repository;

import be.autoservplus.messagerie.domain.Conversation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Suppression des échanges d'un membre avec le garage (F23) : ses fils, avec leurs messages
 * par la cascade de {@code fk_message_conversation}, et ses notifications.
 *
 * <p>Suppression physique et non anonymisation : un message ou une notification n'a pas
 * d'autre contenu que ce que le membre a écrit ou reçu, et aucune obligation ne le fait
 * conserver. Requêtes natives : un DELETE JPQL ne déclencherait pas la cascade, que la base
 * porte déjà.</p>
 */
public interface EchangesSuppressionRepository extends Repository<Conversation, Long> {

    /** @return le nombre de fils supprimés, messages compris */
    @Modifying
    @Query(value = "DELETE FROM conversation WHERE membre_id = :membre", nativeQuery = true)
    int supprimerLesFils(@Param("membre") Long membre);

    /** @return le nombre de notifications supprimées */
    @Modifying
    @Query(value = "DELETE FROM notification WHERE membre_id = :membre", nativeQuery = true)
    int supprimerLesNotifications(@Param("membre") Long membre);
}
