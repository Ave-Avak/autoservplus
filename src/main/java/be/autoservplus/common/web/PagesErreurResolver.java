package be.autoservplus.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Page d erreur du conteneur ({@code /error}) : le gabarit {@code erreur.html} du projet,
 * pour tout statut, a la place de la page Whitelabel de Spring Boot.
 *
 * <p>403, 404 et 500 ont leur titre et leur message ; tout autre statut (400, 405…)
 * recoit un message generique. Le meme gabarit sert deja les erreurs rattrapees par
 * {@code GestionnaireErreursGlobal} : une seule page, un seul retour vers la page
 * d arrivee du role.</p>
 *
 * <p>Un resolveur plutot que quatre gabarits {@code error/*.html} : ceux-ci n auraient
 * fait que relayer {@code erreur.html} avec deux cles, et un statut non prevu serait
 * retombe sur la Whitelabel. Aucun detail technique : le modele de Spring Boot (chemin,
 * exception, trace) n est pas transmis au gabarit.</p>
 */
@Component
public class PagesErreurResolver implements ErrorViewResolver {

    private static final String VUE = "erreur";

    private final MessageSource messages;

    public PagesErreurResolver(MessageSource messages) {
        this.messages = messages;
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest requete, HttpStatus statut,
                                         Map<String, Object> modeleSpring) {
        String prefixe = switch (statut) {
            case FORBIDDEN -> "erreur.403";
            case NOT_FOUND -> "erreur.404";
            case INTERNAL_SERVER_ERROR -> "erreur.500";
            default -> "erreur.generique";
        };
        return new ModelAndView(VUE, Map.of(
                "titre", msg(prefixe + ".titre"),
                "messageErreur", msg(prefixe + ".message")), statut);
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
