package be.autoservplus.navigation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Pose le fil d'Ariane de la page dans le modèle, sous {@code filAriane}, d'après le motif
 * de la route que Spring a retenu. Une route absente de {@link TableFilAriane}, une
 * racine, une étape de connexion ou la page d'erreur du conteneur, ne reçoit rien, et le
 * fragment n'affiche rien.
 */
@ControllerAdvice
public class FilArianeAdvice {

    private final TableFilAriane table = new TableFilAriane();
    private final MessageSource messages;

    public FilArianeAdvice(MessageSource messages) {
        this.messages = messages;
    }

    @ModelAttribute
    @SuppressWarnings("unchecked")
    public void exposerLeFil(HttpServletRequest requete, Model modele) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String patron)) {
            return;
        }
        Map<String, String> variables =
                (Map<String, String>) requete.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        table.entree(patron).ifPresent(page -> modele.addAttribute("filAriane", new FilAriane(
                table, page, variables, modele.asMap(), messages, LocaleContextHolder.getLocale())));
    }
}
