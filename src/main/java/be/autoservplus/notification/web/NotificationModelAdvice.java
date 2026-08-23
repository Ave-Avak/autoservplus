package be.autoservplus.notification.web;

import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.vente.web.HistoriqueCommandeController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose le compteur de notifications non lues ({@code nombreNotificationsNonLues}) aux
 * pages ou le membre suit son dossier.
 *
 * <p><b>Portee volontairement limitee</b>, exactement pour la raison deja documentee sur
 * {@code PanierModelAdvice} : l en-tete est recopie dans 47 gabarits depuis la
 * suppression du layout commun, et porter le compteur sur toute la navigation
 * imposerait de les editer un a un. Ce serait 47 fichiers modifies dans un commit
 * intitule BL-6, pour un changement visuel sans rapport avec les notifications. Le
 * compteur suivra la reintroduction du fragment d en-tete commun (dette documentee),
 * qui le rendra global en une seule edition.</p>
 *
 * <p>Injection par {@link ObjectProvider}, comme {@code PanierModelAdvice} : les tests
 * {@code @WebMvcTest} d autres controleurs instancient tous les
 * {@code @ControllerAdvice} du projet sans fournir {@link NotificationService} — le
 * provider rend l advice inerte (compteur a 0) plutot que de faire echouer leur
 * contexte.</p>
 */
@ControllerAdvice(assignableTypes = {NotificationController.class,
        HistoriqueCommandeController.class})
public class NotificationModelAdvice {

    private final ObjectProvider<NotificationService> notificationService;

    public NotificationModelAdvice(ObjectProvider<NotificationService> notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("nombreNotificationsNonLues")
    public long nombreNonLues(Authentication authentification) {
        // L'anonyme de Spring Security repond isAuthenticated() = true : le test
        // d'instance est necessaire, comme dans PanierModelAdvice et JpaAuditingConfig.
        if (authentification == null
                || !authentification.isAuthenticated()
                || authentification instanceof AnonymousAuthenticationToken) {
            return 0L;
        }
        NotificationService service = notificationService.getIfAvailable();
        return service == null ? 0L : service.nombreNonLues(authentification.getName());
    }
}
