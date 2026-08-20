package be.autoservplus.identite.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Affichage du formulaire de connexion.
 *
 * <p>La soumission n est pas traitee ici : Spring Security intercepte le POST vers
 * /connexion avant qu il n atteigne un controleur. Ce controleur ne sert donc qu a
 * afficher la page et les messages associes.</p>
 */
@Controller
public class ConnexionController {

    @GetMapping("/connexion")
    public String afficherFormulaire(@RequestParam(required = false) String erreur,
                                     @RequestParam(required = false) String bloque,
                                     @RequestParam(required = false) String deconnecte,
                                     Model modele) {
        modele.addAttribute("titre", "Connexion");
        modele.addAttribute("erreur", erreur != null);
        modele.addAttribute("bloque", bloque != null);
        modele.addAttribute("deconnecte", deconnecte != null);
        return "identite/connexion";
    }
}