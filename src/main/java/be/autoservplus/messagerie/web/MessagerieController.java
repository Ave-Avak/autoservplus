package be.autoservplus.messagerie.web;

import be.autoservplus.messagerie.service.MessagerieService;
import be.autoservplus.messagerie.web.dto.FormulaireFil;
import be.autoservplus.messagerie.web.dto.FormulaireReponse;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Messagerie cote membre (BL-5), sous {@code /mes-messages}.
 *
 * <p>La protection d URL exige l authentification ; {@link MessagerieService} redouble
 * par {@code @PreAuthorize} de classe et porte le controle de propriete. L identite
 * vient du contexte de securite, jamais de l URL.</p>
 *
 * <p>POST-redirect-flash sur les deux ecritures. Aucun {@code try/catch} :
 * {@code RessourceIntrouvableException} porte {@code @ResponseStatus(NOT_FOUND)}, un
 * fil inconnu ou d autrui produit donc un 404 sans code supplementaire.</p>
 */
@Controller
@RequestMapping("/mes-messages")
public class MessagerieController {

    private final MessagerieService messagerie;
    private final MessageSource messages;

    public MessagerieController(MessagerieService messagerie, MessageSource messages) {
        this.messagerie = messagerie;
        this.messages = messages;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", msg("messagerie.titre"));
        modele.addAttribute("nombreFilsNonLus", messagerie.nombreFilsNonLus(membre.getUsername()));
        modele.addAttribute("fils", messagerie.mesFils(membre.getUsername()));
        return "messagerie/fils";
    }

    @GetMapping("/nouveau")
    public String formulaire(@ModelAttribute("formulaire") FormulaireFil formulaire, Model modele) {
        modele.addAttribute("titre", msg("messagerie.nouveau.titre"));
        return "messagerie/nouveau";
    }

    @PostMapping("/nouveau")
    public String ouvrir(@AuthenticationPrincipal UserDetails membre,
                         @Valid @ModelAttribute("formulaire") FormulaireFil formulaire,
                         BindingResult resultat,
                         Model modele,
                         RedirectAttributes redirection) {
        if (resultat.hasErrors()) {
            modele.addAttribute("titre", msg("messagerie.nouveau.titre"));
            return "messagerie/nouveau";
        }
        UUID reference = messagerie.ouvrir(membre.getUsername(), formulaire.getSujet(),
                formulaire.getCorps(), formulaire.getIntervention());
        redirection.addFlashAttribute("message", msg("messagerie.envoye"));
        return "redirect:/mes-messages/" + reference;
    }

    @GetMapping("/{reference}")
    public String fil(@AuthenticationPrincipal UserDetails membre,
                      @PathVariable UUID reference,
                      @ModelAttribute("formulaire") FormulaireReponse formulaire,
                      Model modele) {
        var vue = messagerie.lire(membre.getUsername(), reference);
        modele.addAttribute("titre", vue.sujet());
        modele.addAttribute("fil", vue);
        return "messagerie/fil";
    }

    @PostMapping("/{reference}")
    public String repondre(@AuthenticationPrincipal UserDetails membre,
                           @PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") FormulaireReponse formulaire,
                           BindingResult resultat,
                           Model modele,
                           RedirectAttributes redirection) {
        if (resultat.hasErrors()) {
            var vue = messagerie.lire(membre.getUsername(), reference);
            modele.addAttribute("titre", vue.sujet());
            modele.addAttribute("fil", vue);
            return "messagerie/fil";
        }
        try {
            messagerie.repondre(membre.getUsername(), reference, formulaire.getCorps());
        } catch (IllegalStateException e) {
            // Fil cloture entre l'affichage et l'envoi : cas metier, pas une panne.
            redirection.addFlashAttribute("erreur", msg("messagerie.cloture.refus"));
            return "redirect:/mes-messages/" + reference;
        }
        redirection.addFlashAttribute("message", msg("messagerie.envoye"));
        return "redirect:/mes-messages/" + reference;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
