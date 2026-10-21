package be.autoservplus.messagerie.web;

import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.messagerie.service.MessagerieService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose le nombre de conversations a lire ({@code nombreFilsNonLus}) a toutes les
 * pages, pour l en-tete commun. Seule la liste des conversations le posait jusque-la.
 *
 * <p>Meme patron que {@code NotificationModelAdvice} : connexion partielle a zero,
 * service fourni par {@link ObjectProvider} pour les tests {@code @WebMvcTest}.</p>
 */
@ControllerAdvice
public class MessagerieModelAdvice {

    private final ObjectProvider<MessagerieService> messagerie;

    public MessagerieModelAdvice(ObjectProvider<MessagerieService> messagerie) {
        this.messagerie = messagerie;
    }

    @ModelAttribute("nombreFilsNonLus")
    public long nombreFilsNonLus(Authentication authentification) {
        if (!AuthentificationPartielle.aboutie(authentification)) {
            return 0L;
        }
        MessagerieService service = messagerie.getIfAvailable();
        return service == null ? 0L : service.nombreFilsNonLus(authentification.getName());
    }
}
