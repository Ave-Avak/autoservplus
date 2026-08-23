package be.autoservplus.messagerie.web;

import be.autoservplus.messagerie.service.AdminMessagerieService;
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
 * Messagerie cote garage (BL-5), sous {@code /admin/messages}.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link AdminMessagerieService} redouble par {@code @PreAuthorize} en defense en
 * profondeur.</p>
 */
@Controller
@RequestMapping("/admin/messages")
public class AdminMessagerieController {

    private static final String LISTE = "redirect:/admin/messages";

    private final AdminMessagerieService messagerie;
    private final MessageSource messages;

    public AdminMessagerieController(AdminMessagerieService messagerie, MessageSource messages) {
        this.messagerie = messagerie;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", msg("admin.messagerie.titre"));
        modele.addAttribute("fils", messagerie.tous());
        return "admin/messages";
    }

    @GetMapping("/{reference}")
    public String fil(@PathVariable UUID reference,
                      @ModelAttribute("formulaire") FormulaireReponse formulaire,
                      Model modele) {
        var vue = messagerie.lire(reference);
        modele.addAttribute("titre", vue.sujet());
        modele.addAttribute("fil", vue);
        return "admin/message-fil";
    }

    @PostMapping("/{reference}")
    public String repondre(@AuthenticationPrincipal UserDetails administrateur,
                           @PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") FormulaireReponse formulaire,
                           BindingResult resultat,
                           Model modele,
                           RedirectAttributes redirection) {
        if (resultat.hasErrors()) {
            var vue = messagerie.lire(reference);
            modele.addAttribute("titre", vue.sujet());
            modele.addAttribute("fil", vue);
            return "admin/message-fil";
        }
        try {
            messagerie.repondre(administrateur.getUsername(), reference, formulaire.getCorps());
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", msg("messagerie.cloture.refus"));
            return "redirect:/admin/messages/" + reference;
        }
        redirection.addFlashAttribute("message", msg("messagerie.envoye"));
        return "redirect:/admin/messages/" + reference;
    }

    @PostMapping("/{reference}/cloturer")
    public String cloturer(@PathVariable UUID reference, RedirectAttributes redirection) {
        messagerie.cloturer(reference);
        redirection.addFlashAttribute("message", msg("admin.messagerie.cloture"));
        return LISTE;
    }

    @PostMapping("/{reference}/rouvrir")
    public String rouvrir(@PathVariable UUID reference, RedirectAttributes redirection) {
        messagerie.rouvrir(reference);
        redirection.addFlashAttribute("message", msg("admin.messagerie.rouvert"));
        return LISTE;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
