package be.autoservplus.notification.web;

import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.notification.service.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose le compteur de notifications non lues ({@code nombreNotificationsNonLues}) a
 * toutes les pages, pour l en-tete commun. Limite a deux ecrans tant que l en-tete
 * etait recopie page par page. Le fragment commun le rend global en une edition.
 *
 * <p>Le comptage s appuie sur {@code ix_notification_membre (membre_id, statut)}.</p>
 *
 * <p>Injection par {@link ObjectProvider}, comme {@code PanierModelAdvice} : les tests
 * {@code @WebMvcTest} d autres controleurs instancient tous les
 * {@code @ControllerAdvice} du projet sans fournir {@link NotificationService}. Le
 * provider rend l advice inerte (compteur a 0) plutot que de faire echouer leur
 * contexte.</p>
 */
@ControllerAdvice
public class NotificationModelAdvice {

    private final ObjectProvider<NotificationService> notificationService;

    public NotificationModelAdvice(ObjectProvider<NotificationService> notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("nombreNotificationsNonLues")
    public long nombreNonLues(Authentication authentification) {
        if (!AuthentificationPartielle.aboutie(authentification)) {
            return 0L;
        }
        NotificationService service = notificationService.getIfAvailable();
        return service == null ? 0L : service.nombreNonLues(authentification.getName());
    }
}
