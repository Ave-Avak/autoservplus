package be.autoservplus.rgpd.repository;

import be.autoservplus.notification.domain.Notification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Notifications du membre pour l'export (F22), dans l'ordre chronologique. */
public interface NotificationExportRepository extends Repository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n
            WHERE n.membre.email = :email
            ORDER BY n.dateEnvoi, n.id
            """)
    List<Notification> pourMembre(@Param("email") String email);
}
