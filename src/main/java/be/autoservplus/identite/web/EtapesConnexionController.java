package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.identite.mfa.CodeQrSvg;
import be.autoservplus.identite.service.PremierMotDePasseService;
import be.autoservplus.identite.service.SecondFacteurService;
import be.autoservplus.identite.web.dto.MotDePasseImposeForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Ecrans des etapes qui suivent le mot de passe : second facteur, changement impose,
 * enrolement.
 *
 * <p>Chaque ecran commence par {@link ParcoursConnexion#garder} : une connexion partielle
 * ne choisit pas son etape, et un compte connecte n a rien a faire ici. Ce sont les seules
 * routes, avec la deconnexion, qu une connexion partielle peut atteindre.</p>
 */
@Controller
@RequestMapping(EtapeConnexion.PREFIXE)
public class EtapesConnexionController {

    static final String VUE_SECOND_FACTEUR = "identite/connexion-second-facteur";
    static final String VUE_MOT_DE_PASSE = "identite/mot-de-passe-a-definir";
    static final String VUE_ENROLEMENT = "identite/enrolement";
    private static final String MODE_SECOURS = "secours";

    private final ParcoursConnexion parcours;
    private final SecondFacteurService secondFacteur;
    private final PremierMotDePasseService premierMotDePasse;
    private final MessageSource messages;

    public EtapesConnexionController(ParcoursConnexion parcours, SecondFacteurService secondFacteur,
                                     PremierMotDePasseService premierMotDePasse,
                                     MessageSource messages) {
        this.parcours = parcours;
        this.secondFacteur = secondFacteur;
        this.premierMotDePasse = premierMotDePasse;
        this.messages = messages;
    }

    /** Point d entree du gestionnaire de refus : il ne connait que le prefixe. */
    @GetMapping
    public String aiguiller(Authentication authentification) {
        String garde = parcours.garder(authentification, null);
        return garde == null ? "redirect:/mon-compte" : garde;
    }

    // --- second facteur ---------------------------------------------------------------

    @GetMapping("/second-facteur")
    public String secondFacteur(Authentication authentification, Model modele) {
        String garde = parcours.garder(authentification, EtapeConnexion.SECOND_FACTEUR);
        if (garde != null) {
            return garde;
        }
        modele.addAttribute("titre", msg("connexion.etape.second-facteur.titre"));
        return VUE_SECOND_FACTEUR;
    }

    @PostMapping("/second-facteur")
    public String verifierSecondFacteur(Authentication authentification,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) String mode,
                                        Model modele, HttpServletRequest requete,
                                        HttpServletResponse reponse) {
        String garde = parcours.garder(authentification, EtapeConnexion.SECOND_FACTEUR);
        if (garde != null) {
            return garde;
        }
        AuthentificationPartielle partielle = (AuthentificationPartielle) authentification;
        return switch (secondFacteur.verifier(partielle.getName(), code)) {
            case ACCEPTE -> parcours.avancer(requete, reponse,
                    partielle.avecSecondFacteurValide(), "/mon-compte");
            case VERROUILLE -> {
                // La connexion partielle tombe : le verrouillage vaut pour le mot de
                // passe aussi, et laisser l etape ouverte permettrait de continuer a
                // essayer en attendant la fin du verrouillage.
                parcours.abandonner(requete, reponse);
                yield "redirect:/connexion?bloque";
            }
            case REFUSE -> {
                modele.addAttribute("titre", msg("connexion.etape.second-facteur.titre"));
                modele.addAttribute("erreur", msg("connexion.etape.second-facteur.erreur"));
                // Le champ de secours ne sert qu a la saisie ; le verdict est celui du
                // meme service. Le rouvrir evite de faire chercher a nouveau le lien.
                modele.addAttribute("secoursOuvert", MODE_SECOURS.equals(mode));
                yield VUE_SECOND_FACTEUR;
            }
        };
    }

    // --- changement impose ------------------------------------------------------------

    @GetMapping("/mot-de-passe")
    public String motDePasse(Authentication authentification, Model modele) {
        String garde = parcours.garder(authentification, EtapeConnexion.CHANGEMENT_MOT_DE_PASSE);
        if (garde != null) {
            return garde;
        }
        modele.addAttribute("formulaire", new MotDePasseImposeForm());
        preparerMotDePasse(modele);
        return VUE_MOT_DE_PASSE;
    }

    @PostMapping("/mot-de-passe")
    public String changerMotDePasse(Authentication authentification,
                                    @Valid @ModelAttribute("formulaire") MotDePasseImposeForm formulaire,
                                    BindingResult erreurs, Model modele,
                                    HttpServletRequest requete, HttpServletResponse reponse) {
        String garde = parcours.garder(authentification, EtapeConnexion.CHANGEMENT_MOT_DE_PASSE);
        if (garde != null) {
            return garde;
        }
        if (!formulaire.concordent()) {
            erreurs.rejectValue("confirmation", "motsDePasse.differents",
                    msg("mot-de-passe-a-definir.erreur.differents"));
        }
        if (!erreurs.hasErrors()) {
            try {
                premierMotDePasse.changerImpose(authentification.getName(), formulaire.getMotDePasse());
                return parcours.avancer(requete, reponse,
                        (AuthentificationPartielle) authentification, "/mon-compte");
            } catch (RefusTraduit refus) {
                erreurs.rejectValue("motDePasse", "motDePasse.identique", msg(refus.getCleMessage()));
            }
        }
        preparerMotDePasse(modele);
        return VUE_MOT_DE_PASSE;
    }

    private void preparerMotDePasse(Model modele) {
        modele.addAttribute("titre", msg("mot-de-passe-a-definir.titre"));
        modele.addAttribute("action", EtapeConnexion.CHANGEMENT_MOT_DE_PASSE.chemin());
        modele.addAttribute("enConnexion", true);
    }

    // --- enrolement -------------------------------------------------------------------

    @GetMapping("/enrolement")
    public String enrolement(Authentication authentification, Model modele) {
        String garde = parcours.garder(authentification, EtapeConnexion.ENROLEMENT);
        if (garde != null) {
            return garde;
        }
        preparerEnrolement(authentification.getName(), modele);
        return VUE_ENROLEMENT;
    }

    @PostMapping("/enrolement")
    public String confirmerEnrolement(Authentication authentification,
                                      @RequestParam(required = false) String code,
                                      Model modele, HttpServletRequest requete,
                                      HttpServletResponse reponse,
                                      RedirectAttributes redirection) {
        String garde = parcours.garder(authentification, EtapeConnexion.ENROLEMENT);
        if (garde != null) {
            return garde;
        }
        try {
            List<String> codes = secondFacteur.confirmerEnrolement(authentification.getName(),
                    code, requete.getSession().getId());
            redirection.addFlashAttribute("codesSecours", codes);
            // Le code qui vient de confirmer l enrolement prouve la possession du
            // telephone : le redemander dans la foulee n ajouterait rien.
            return parcours.avancer(requete, reponse,
                    ((AuthentificationPartielle) authentification).avecSecondFacteurValide(),
                    SecuriteCompteController.CHEMIN_CODES);
        } catch (RefusTraduit refus) {
            modele.addAttribute("erreur", msg(refus.getCleMessage()));
            preparerEnrolement(authentification.getName(), modele);
            return VUE_ENROLEMENT;
        }
    }

    private void preparerEnrolement(String email, Model modele) {
        SecondFacteurService.Enrolement enrolement = secondFacteur.preparerEnrolement(email);
        modele.addAttribute("titre", msg("enrolement.titre"));
        modele.addAttribute("cle", enrolement.cleLisible());
        modele.addAttribute("codeQr", CodeQrSvg.svg(enrolement.uriOtpauth(), msg("enrolement.qr.aria")));
        modele.addAttribute("action", EtapeConnexion.ENROLEMENT.chemin());
        modele.addAttribute("enConnexion", true);
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
