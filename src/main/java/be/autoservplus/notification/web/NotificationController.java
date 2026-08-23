package be.autoservplus.notification.web;

import be.autoservplus.notification.service.NotificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Ecran « Mes notifications » (BL-6).
 *
 * <p>La protection d URL de {@code SecuriteConfig} exige deja une authentification
 * ({@code anyRequest().authenticated()}) ; {@link NotificationService} redouble par
 * {@code @PreAuthorize} en defense en profondeur, et porte le controle de propriete.
 * L identite vient du contexte de securite, jamais d un parametre de requete.</p>
 *
 * <p>POST-redirect-flash sur les deux actions : un rafraichissement apres marquage ne
 * doit pas rejouer la requete. Aucun {@code try/catch} —
 * {@code RessourceIntrouvableException} porte {@code @ResponseStatus(NOT_FOUND)}, donc
 * un identifiant inconnu ou une notification d autrui produit un 404 sans code
 * supplementaire.</p>
 */
@Controller
@RequestMapping("/mes-notifications")
public class NotificationController {

    private static final String LISTE = "redirect:/mes-notifications";

    private final NotificationService notifications;
    private final MessageSource messages;

    public NotificationController(NotificationService notifications, MessageSource messages) {
        this.notifications = notifications;
        this.messages = messages;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", msg("notifications.titre"));
        modele.addAttribute("notifications", notifications.mesNotifications(membre.getUsername()));
        return "notification/notifications";
    }

    @PostMapping("/{id}/lue")
    public String marquerLue(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable Long id,
                             RedirectAttributes redirection) {
        notifications.marquerLue(membre.getUsername(), id);
        redirection.addFlashAttribute("message", msg("notifications.marquee"));
        return LISTE;
    }

    @PostMapping("/tout-lu")
    public String marquerToutesLues(@AuthenticationPrincipal UserDetails membre,
                                    RedirectAttributes redirection) {
        notifications.marquerToutesLues(membre.getUsername());
        redirection.addFlashAttribute("message", msg("notifications.toutes-marquees"));
        return LISTE;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
