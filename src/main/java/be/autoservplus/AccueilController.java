package be.autoservplus;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Pages generales : accueil public et espace membre. */
@Controller
public class AccueilController {

    private final MessageSource messages;

    public AccueilController(MessageSource messages) {
        this.messages = messages;
    }

    @GetMapping({"/", "/accueil"})
    public String accueil(Model modele) {
        modele.addAttribute("titre", "Accueil");
        return "accueil";
    }

    @GetMapping("/mon-compte")
    public String monCompte(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre",
                messages.getMessage("compte.titre", null, LocaleContextHolder.getLocale()));
        modele.addAttribute("email", membre.getUsername());
        return "mon-compte";
    }
}