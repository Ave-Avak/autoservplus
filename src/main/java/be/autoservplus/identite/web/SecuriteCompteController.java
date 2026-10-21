package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.identite.mfa.CodeQrSvg;
import be.autoservplus.identite.service.SecondFacteurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Securite du compte depuis le profil : activer, retirer le second facteur, regenerer les
 * codes de secours.
 *
 * <p>Facultatif pour un membre, obligatoire au back-office — un administrateur y vient
 * pour regenerer ses codes, jamais pour retirer son second facteur, que le service lui
 * refuse.</p>
 *
 * <p><b>Les codes de secours ne transitent que par un attribut flash</b> : affiches une
 * fois, sur la page qui suit leur emission, puis perdus. Les rendre a nouveau supposerait
 * de les garder en clair quelque part ; ils ne sont gardes qu en BCrypt.</p>
 */
@Controller
@RequestMapping(SecuriteCompteController.CHEMIN)
public class SecuriteCompteController {

    static final String CHEMIN = "/mon-profil/securite";
    static final String CHEMIN_CODES = CHEMIN + "/codes";
    private static final String MODE_SECOURS = "secours";

    private final SecondFacteurService secondFacteur;
    private final MessageSource messages;

    public SecuriteCompteController(SecondFacteurService secondFacteur, MessageSource messages) {
        this.secondFacteur = secondFacteur;
        this.messages = messages;
    }

    @GetMapping
    public String afficher(@AuthenticationPrincipal UserDetails titulaire, Model modele) {
        modele.addAttribute("etat", secondFacteur.etat(titulaire.getUsername()));
        modele.addAttribute("titre", msg("securite.titre"));
        return "identite/securite";
    }

    @GetMapping("/enrolement")
    public String enrolement(@AuthenticationPrincipal UserDetails titulaire, Model modele) {
        if (secondFacteur.etat(titulaire.getUsername()).actif()) {
            return "redirect:" + CHEMIN;
        }
        preparerEnrolement(titulaire.getUsername(), modele);
        return EtapesConnexionController.VUE_ENROLEMENT;
    }

    @PostMapping("/enrolement")
    public String confirmerEnrolement(@AuthenticationPrincipal UserDetails titulaire,
                                      @RequestParam(required = false) String code,
                                      HttpServletRequest requete, Model modele,
                                      RedirectAttributes redirection) {
        try {
            List<String> codes = secondFacteur.confirmerEnrolement(titulaire.getUsername(), code,
                    requete.getSession().getId());
            redirection.addFlashAttribute("codesSecours", codes);
            return "redirect:" + CHEMIN_CODES;
        } catch (RefusTraduit refus) {
            modele.addAttribute("erreur", msg(refus.getCleMessage()));
            preparerEnrolement(titulaire.getUsername(), modele);
            return EtapesConnexionController.VUE_ENROLEMENT;
        }
    }

    /**
     * Ecran d affichage des codes qui viennent d etre emis. Sans attribut flash — acces
     * direct, rafraichissement —, il n y a rien a montrer : retour a l ecran de securite,
     * et non une page vide qui laisserait croire les codes perdus.
     */
    @GetMapping("/codes")
    public String codes(Model modele) {
        if (!modele.containsAttribute("codesSecours")) {
            return "redirect:" + CHEMIN;
        }
        modele.addAttribute("titre", msg("codes-secours.titre"));
        return "identite/codes-secours";
    }

    @PostMapping("/codes")
    public String regenererCodes(@AuthenticationPrincipal UserDetails titulaire,
                                 @RequestParam(required = false) String motDePasse,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String mode,
                                 RedirectAttributes redirection) {
        try {
            redirection.addFlashAttribute("codesSecours",
                    secondFacteur.regenererCodes(titulaire.getUsername(), motDePasse, code));
            return "redirect:" + CHEMIN_CODES;
        } catch (RefusTraduit refus) {
            refuser(redirection, refus, mode, "codes");
            return "redirect:" + CHEMIN;
        }
    }

    @PostMapping("/desactivation")
    public String desactiver(@AuthenticationPrincipal UserDetails titulaire,
                             @RequestParam(required = false) String motDePasse,
                             @RequestParam(required = false) String code,
                             @RequestParam(required = false) String mode,
                             HttpServletRequest requete, RedirectAttributes redirection) {
        try {
            secondFacteur.desactiver(titulaire.getUsername(), motDePasse, code,
                    requete.getSession().getId());
            redirection.addFlashAttribute("message", msg("securite.desactive"));
        } catch (RefusTraduit refus) {
            refuser(redirection, refus, mode, "desactivation");
        }
        return "redirect:" + CHEMIN;
    }

    /**
     * Le refus est celui du service, quel que soit le champ employe ; seul l affichage
     * suit : un refus venu d un champ de secours rouvre ce champ-la, et pas l autre.
     */
    private void refuser(RedirectAttributes redirection, RefusTraduit refus, String mode,
                         String formulaire) {
        redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
        if (MODE_SECOURS.equals(mode)) {
            redirection.addFlashAttribute("secoursOuvert", formulaire);
        }
    }

    private void preparerEnrolement(String email, Model modele) {
        SecondFacteurService.Enrolement enrolement = secondFacteur.preparerEnrolement(email);
        modele.addAttribute("titre", msg("enrolement.titre"));
        modele.addAttribute("cle", enrolement.cleLisible());
        modele.addAttribute("codeQr", CodeQrSvg.svg(enrolement.uriOtpauth(), msg("enrolement.qr.aria")));
        modele.addAttribute("action", CHEMIN + "/enrolement");
        modele.addAttribute("enConnexion", false);
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
