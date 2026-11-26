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
 * <p>403, 404, 409, 422 et 500 ont leur titre et leur message. Le 422 d'un refus métier
 * affiche le message de ce refus quand il en porte un. Tout autre statut (400, 405...)
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

    /**
     * Attribut de requête posé par {@link GestionnaireErreursGlobal} : le message du
     * refus métier à afficher à la place du texte générique du 422.
     */
    public static final String ATTRIBUT_REFUS = PagesErreurResolver.class.getName() + ".refus";

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
            case CONFLICT -> "erreur.409";
            case UNPROCESSABLE_ENTITY -> "erreur.422";
            case INTERNAL_SERVER_ERROR -> "erreur.500";
            default -> "erreur.generique";
        };
        String message = requete.getAttribute(ATTRIBUT_REFUS) instanceof RefusAffiche refus
                && statut == HttpStatus.UNPROCESSABLE_ENTITY
                ? messageDuRefus(refus, prefixe)
                : msg(prefixe + ".message", null);
        return new ModelAndView(VUE, Map.of(
                "titre", msg(prefixe + ".titre", null),
                "messageErreur", message), statut);
    }

    /**
     * Une clé absente des fichiers retombe sur le texte générique : afficher la clé brute
     * serait pire que le message général, et le défaut se voit au journal du refus.
     */
    private String messageDuRefus(RefusAffiche refus, String prefixe) {
        String generique = msg(prefixe + ".message", null);
        if (refus.cle() == null) {
            return generique;
        }
        return messages.getMessage(refus.cle(), refus.arguments(), generique, LocaleContextHolder.getLocale());
    }

    private String msg(String cle, Object[] arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }

    /** Clé et valeurs du message d'un refus métier, transmises de l'exception à la page. */
    public record RefusAffiche(String cle, Object[] arguments) {
    }
}
