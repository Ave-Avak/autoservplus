package be.autoservplus;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Pages generales : accueil public et espace membre. */
@Controller
public class AccueilController {

    @GetMapping({"/", "/accueil"})
    public String accueil(Model modele) {
        modele.addAttribute("titre", "Accueil");
        return "accueil";
    }

    @GetMapping("/mon-compte")
    public String monCompte(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", "Mon compte");
        modele.addAttribute("email", membre.getUsername());
        return "mon-compte";
    }
}