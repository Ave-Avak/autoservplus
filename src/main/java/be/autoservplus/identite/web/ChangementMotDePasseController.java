package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.identite.service.ChangementMotDePasseService;
import be.autoservplus.identite.service.MotDePasseActuelIncorrectException;
import be.autoservplus.identite.web.dto.ChangementMotDePasseForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * « Changer mon mot de passe », depuis le profil.
 *
 * <p>Les échecs du mot de passe actuel sont comptés dans la session, et non sur le
 * compte : la mesure vise une session volée qui essaierait des mots de passe, pas le
 * titulaire. Au cinquième, la session est fermée et l'événement consigné, le compte
 * restant ouvert à une nouvelle connexion. Un compteur sur le compte permettrait à un
 * tiers de le verrouiller, ce que le limiteur de la connexion fait déjà.</p>
 */
@Controller
@RequestMapping(ChangementMotDePasseController.CHEMIN)
public class ChangementMotDePasseController {

    static final String CHEMIN = "/mon-profil/mot-de-passe";
    static final int ECHECS_MAXIMUM = 5;
    static final String ATTRIBUT_ECHECS = ChangementMotDePasseController.class.getName() + ".echecs";

    private static final String VUE = "identite/mot-de-passe-changement";

    private final ChangementMotDePasseService service;
    private final MessageSource messages;

    public ChangementMotDePasseController(ChangementMotDePasseService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String afficher(Model modele) {
        modele.addAttribute("formulaire", new ChangementMotDePasseForm());
        return VUE;
    }

    @PostMapping
    public String changer(@AuthenticationPrincipal UserDetails titulaire,
                          @Valid @ModelAttribute("formulaire") ChangementMotDePasseForm formulaire,
                          BindingResult erreurs, HttpServletRequest requete,
                          RedirectAttributes redirection) {
        if (!formulaire.concordent()) {
            erreurs.rejectValue("confirmation", "motsDePasse.differents",
                    msg("mot-de-passe-a-definir.erreur.differents"));
        }
        if (erreurs.hasErrors()) {
            return VUE;
        }
        HttpSession session = requete.getSession();
        try {
            service.changer(titulaire.getUsername(), formulaire.getActuel(),
                    formulaire.getMotDePasse(), session.getId());
        } catch (MotDePasseActuelIncorrectException refus) {
            if (echecSupplementaire(session) >= ECHECS_MAXIMUM) {
                service.consignerBlocage(titulaire.getUsername());
                session.invalidate();
                SecurityContextHolder.clearContext();
                return "redirect:/connexion?session-fermee";
            }
            erreurs.rejectValue("actuel", "motDePasse.actuel", msg(refus.getCleMessage()));
            return VUE;
        } catch (RefusTraduit refus) {
            erreurs.rejectValue("motDePasse", "motDePasse.refuse", msg(refus.getCleMessage()));
            return VUE;
        }
        session.removeAttribute(ATTRIBUT_ECHECS);
        // Nouvel identifiant après le changement du secret : un identifiant de session
        // capté avant le changement ne vaut plus rien après.
        requete.changeSessionId();
        redirection.addFlashAttribute("motDePasseChange", true);
        return "redirect:/mon-profil";
    }

    private static int echecSupplementaire(HttpSession session) {
        Integer echecs = (Integer) session.getAttribute(ATTRIBUT_ECHECS);
        int total = echecs == null ? 1 : echecs + 1;
        session.setAttribute(ATTRIBUT_ECHECS, total);
        return total;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
