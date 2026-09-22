package be.autoservplus.identite.web;

import be.autoservplus.identite.service.ChangementEmailRefuseException;
import be.autoservplus.identite.service.ChangementEmailService;
import be.autoservplus.identite.web.dto.ChangementEmailForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Changement d adresse de courriel, en deux temps (CdC 5.2.2).
 *
 * <p>La demande est authentifiee, la confirmation ne l est pas : le lien arrive dans
 * la NOUVELLE boite, souvent ouverte dans un autre navigateur, et exiger une session
 * y renverrait vers un formulaire de connexion portant l ancienne adresse. Le jeton
 * porte a lui seul l autorisation, comme pour l activation de compte.</p>
 */
@Controller
public class ChangementEmailController {

    private final ChangementEmailService service;
    private final MessageSource messages;

    public ChangementEmailController(ChangementEmailService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping("/mon-profil/adresse")
    public String formulaire(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("formulaire", new ChangementEmailForm());
        modele.addAttribute("email", membre.getUsername());
        return "identite/changement-email";
    }

    @PostMapping("/mon-profil/adresse")
    public String demander(@AuthenticationPrincipal UserDetails membre,
                           @Valid @ModelAttribute("formulaire") ChangementEmailForm formulaire,
                           BindingResult erreurs,
                           HttpServletRequest requete,
                           Model modele,
                           RedirectAttributes redirection) {

        if (!erreurs.hasErrors()) {
            try {
                service.demander(membre.getUsername(), formulaire.getNouvelleAdresse(),
                        formulaire.getMotDePasse(), requete.getRemoteAddr());
            } catch (ChangementEmailRefuseException refus) {
                erreurs.reject(refus.getCleMessage(), traduire(refus));
            }
        }

        if (erreurs.hasErrors()) {
            modele.addAttribute("email", membre.getUsername());
            return "identite/changement-email";
        }

        // Message NEUTRE, et volontairement le meme que l adresse visee soit libre ou
        // deja prise : le distinguer ferait de ce formulaire un oracle d existence de
        // compte, que le service s emploie precisement a taire.
        redirection.addFlashAttribute("demandeEnregistree", true);
        return "redirect:/mon-profil";
    }

    /**
     * Applique le changement puis <b>coupe la session</b>.
     *
     * <p>Le nom d utilisateur EST l adresse de courriel : apres la bascule, le
     * principal en session designe une adresse que plus aucune ligne ne porte. Le
     * laisser vivre donnerait une session dont l identite ne se resout plus — mieux
     * vaut une reconnexion explicite, qui a le merite de prouver que le membre connait
     * toujours son mot de passe. Meme geste qu a la suppression de compte (F23).</p>
     */
    @GetMapping("/changement-adresse/confirmation")
    public String confirmer(@RequestParam("jeton") String jeton,
                            HttpServletRequest requete, HttpServletResponse reponse,
                            Model modele) {
        try {
            service.confirmer(jeton);
        } catch (ChangementEmailRefuseException refus) {
            modele.addAttribute("echec", traduire(refus));
            return "identite/changement-email-resultat";
        }

        Authentication session = SecurityContextHolder.getContext().getAuthentication();
        if (session != null) {
            new SecurityContextLogoutHandler().logout(requete, reponse, session);
        }
        modele.addAttribute("echec", null);
        return "identite/changement-email-resultat";
    }

    /**
     * Le refus PORTE sa cle i18n : il n y a plus de table a tenir a jour, donc plus
     * de refus nouveau qui s afficherait en francais faute d y avoir ete inscrit.
     */
    private String traduire(ChangementEmailRefuseException refus) {
        return messages.getMessage(refus.getCleMessage(), null,
                LocaleContextHolder.getLocale());
    }
}
