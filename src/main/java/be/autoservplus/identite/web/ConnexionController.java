package be.autoservplus.identite.web;

import be.autoservplus.config.EchecAuthentificationHandler;
import be.autoservplus.identite.service.GardeConnexion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>Le widget Turnstile n'apparaît que lorsque la prochaine tentative l'exigera : d'après
 * l'IP, ou d'après l'adresse saisie à la tentative précédente, que le refus a notée en
 * session. Ni l'un ni l'autre ne dépend de l'existence d'un compte.</p>
 */
@Controller
public class ConnexionController {

    private final GardeConnexion garde;
    private final String cleTurnstile;

    public ConnexionController(GardeConnexion garde,
                               @Value("${autoservplus.securite.turnstile.cle-site:}")
                               String cleTurnstile) {
        this.garde = garde;
        this.cleTurnstile = cleTurnstile;
    }

    @GetMapping("/connexion")
    public String afficherFormulaire(@RequestParam(required = false) String erreur,
                                     @RequestParam(required = false) String deconnecte,
                                     HttpServletRequest requete, Model modele) {
        modele.addAttribute("titre", "Connexion");
        modele.addAttribute("erreur", erreur != null);
        modele.addAttribute("deconnecte", deconnecte != null);
        HttpSession session = requete.getSession(false);
        boolean notee = session != null && Boolean.TRUE.equals(
                session.getAttribute(EchecAuthentificationHandler.VERIFICATION_HUMAINE));
        if (notee || garde.exigeVerificationHumaine(requete.getRemoteAddr())) {
            modele.addAttribute("cleTurnstile", cleTurnstile);
        }
        return "identite/connexion";
    }
}
