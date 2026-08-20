package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.service.MotDePasseService;
import be.autoservplus.identite.web.dto.NouveauMotDePasseForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * Parcours de reinitialisation du mot de passe.
 *
 * <p>La page de confirmation est identique que l adresse existe ou non : c est le
 * courriel, et lui seul, qui renseigne l utilisateur sur la situation de son compte.</p>
 */
@Controller
@RequestMapping("/mot-de-passe")
public class MotDePasseController {

    private final MotDePasseService service;

    public MotDePasseController(MotDePasseService service) {
        this.service = service;
    }

    @GetMapping("/oublie")
    public String afficherDemande(Model modele) {
        modele.addAttribute("titre", "Mot de passe oublié");
        return "identite/mot-de-passe-oublie";
    }

    @PostMapping("/oublie")
    public String traiterDemande(@RequestParam String email, Model modele) {
        service.demanderReinitialisation(email);
        modele.addAttribute("titre", "Vérifiez votre courriel");
        modele.addAttribute("adresse", email);
        return "identite/mot-de-passe-demande";
    }

    @GetMapping("/nouveau")
    public String afficherFormulaire(@RequestParam String jeton, Model modele) {
        modele.addAttribute("titre", "Nouveau mot de passe");
        try {
            service.verifierJeton(jeton);
        } catch (RessourceIntrouvableException | RegleMetierException e) {
            modele.addAttribute("motif", e.getMessage());
            return "identite/mot-de-passe-lien-invalide";
        }
        NouveauMotDePasseForm formulaire = new NouveauMotDePasseForm();
        formulaire.setJeton(jeton);
        modele.addAttribute("formulaire", formulaire);
        return "identite/mot-de-passe-nouveau";
    }

    @PostMapping("/nouveau")
    public String appliquer(@Valid @ModelAttribute("formulaire") NouveauMotDePasseForm formulaire,
                            BindingResult erreurs, Model modele) {

        modele.addAttribute("titre", "Nouveau mot de passe");

        if (!formulaire.concordent()) {
            erreurs.rejectValue("confirmation", "motsDePasse.differents",
                    "Les deux mots de passe ne correspondent pas.");
        }
        if (erreurs.hasErrors()) {
            return "identite/mot-de-passe-nouveau";
        }

        try {
            service.reinitialiser(formulaire.getJeton(), formulaire.getMotDePasse());
        } catch (RessourceIntrouvableException | RegleMetierException e) {
            modele.addAttribute("motif", e.getMessage());
            return "identite/mot-de-passe-lien-invalide";
        }

        modele.addAttribute("titre", "Mot de passe modifié");
        return "identite/mot-de-passe-modifie";
    }
}