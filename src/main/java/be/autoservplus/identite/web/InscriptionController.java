package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.service.InscriptionService;
import be.autoservplus.identite.web.dto.InscriptionForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Parcours d inscription et de verification d adresse.
 *
 * <p>Le controleur ne porte aucune regle metier : il valide la forme des donnees,
 * delegue au service, et traduit le resultat en vue. Toute logique reste dans
 * {@link InscriptionService}.</p>
 */
@Controller
public class InscriptionController {

    private final InscriptionService service;

    public InscriptionController(InscriptionService service) {
        this.service = service;
    }

    @GetMapping("/inscription")
    public String afficherFormulaire(Model modele) {
        modele.addAttribute("titre", "Créer un compte");
        modele.addAttribute("formulaire", new InscriptionForm());
        return "identite/inscription";
    }

    @PostMapping("/inscription")
    public String traiterFormulaire(@Valid @ModelAttribute("formulaire") InscriptionForm formulaire,
                                    BindingResult erreurs,
                                    Model modele) {

        if (!formulaire.motsDePasseConcordent()) {
            erreurs.rejectValue("confirmationMotDePasse", "motsDePasse.differents",
                    "Les deux mots de passe ne correspondent pas.");
        }

        if (erreurs.hasErrors()) {
            modele.addAttribute("titre", "Créer un compte");
            return "identite/inscription";
        }

        try {
            service.inscrire(formulaire.getEmail(), formulaire.getMotDePasse(),
                    formulaire.getNom(), formulaire.getPrenom(), formulaire.getLangue());
        } catch (RegleMetierException e) {
            erreurs.addError(new FieldError("formulaire", "email", e.getMessage()));
            modele.addAttribute("titre", "Créer un compte");
            return "identite/inscription";
        }

        modele.addAttribute("titre", "Vérifiez votre courriel");
        modele.addAttribute("adresse", formulaire.getEmail());
        return "identite/inscription-confirmee";
    }

    /**
     * Formulaire public de renvoi du courriel de verification.
     *
     * <p>La route vit sous /inscription, deja ouverte a l anonyme par
     * SecuriteConfig : c est la suite du parcours d inscription, et la placer ailleurs
     * obligerait a elargir la surface publique pour un ecran qui n en a pas besoin.</p>
     */
    @GetMapping("/inscription/renvoyer-verification")
    public String afficherRenvoiVerification() {
        return "identite/renvoyer-verification";
    }

    /**
     * Traite la demande de renvoi.
     *
     * <p>Rend TOUJOURS la meme vue, sans exposer l adresse saisie ni le sort reel de la
     * demande : le service ne remonte rien qui permettrait de les distinguer. C est le
     * courriel, et lui seul, qui renseigne le titulaire du compte.</p>
     */
    @PostMapping("/inscription/renvoyer-verification")
    public String traiterRenvoiVerification(@RequestParam String email) {
        service.demanderRenvoiVerification(email);
        return "identite/renvoyer-verification-envoye";
    }

    @GetMapping("/inscription/verification")
    public String verifierAdresse(@RequestParam String jeton, Model modele) {
        modele.addAttribute("titre", "Vérification de votre adresse");
        try {
            service.confirmerAdresse(jeton);
            modele.addAttribute("succes", true);
        } catch (RessourceIntrouvableException | RegleMetierException e) {
            modele.addAttribute("succes", false);
            modele.addAttribute("motif", e.getMessage());
        }
        return "identite/verification";
    }
}