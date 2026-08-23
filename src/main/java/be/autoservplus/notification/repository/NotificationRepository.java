package be.autoservplus.notification.repository;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.notification.domain.Notification;
import be.autoservplus.notification.domain.StatutNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Notifications du membre, la plus recente d abord. */
    List<Notification> findByMembreOrderByDateEnvoiDescIdDesc(Utilisateur membre);

    long countByMembreAndStatut(Utilisateur membre, StatutNotification statut);

    /**
     * Charge une notification <b>par son identifiant et son proprietaire a la fois</b>.
     * Le membre n est pas un filtre de confort : sans lui, l identifiant numerique
     * sequentiel de la table suffirait a lire ou marquer la notification d autrui.
     */
    Optional<Notification> findByIdAndMembre(Long id, Utilisateur membre);

    List<Notification> findByMembreAndStatut(Utilisateur membre, StatutNotification statut);
}
