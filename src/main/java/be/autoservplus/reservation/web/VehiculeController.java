package be.autoservplus.reservation.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.service.VehiculeService;
import be.autoservplus.reservation.web.dto.VehiculeForm;
import be.autoservplus.reservation.web.dto.VehiculeVue;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Gestion du parc de vehicules par le membre.
 *
 * <p>Toutes les routes sont derriere authentification. L identite du membre provient du
 * contexte de securite, jamais d un parametre de requete : sans cela, il suffirait de
 * modifier un champ cache pour agir au nom d autrui.</p>
 */
@Controller
@RequestMapping("/mes-vehicules")
public class VehiculeController {

    private final VehiculeService service;

    public VehiculeController(VehiculeService service) {
        this.service = service;
    }

    @GetMapping
    public String lister(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", "Mes véhicules");
        modele.addAttribute("vehicules", service.vuesDuMembre(membre.getUsername()));
        return "reservation/vehicules";
    }

    @GetMapping("/nouveau")
    public String afficherAjout(Model modele) {
        modele.addAttribute("titre", "Ajouter un véhicule");
        modele.addAttribute("formulaire", new VehiculeForm());
        modele.addAttribute("motorisations", Motorisation.values());
        modele.addAttribute("modeAjout", true);
        return "reservation/vehicule-formulaire";
    }

    @PostMapping("/nouveau")
    public String ajouter(@AuthenticationPrincipal UserDetails membre,
                          @Valid @ModelAttribute("formulaire") VehiculeForm formulaire,
                          BindingResult erreurs,
                          Model modele,
                          RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            return retourFormulaire(modele, true);
        }

        try {
            service.ajouter(membre.getUsername(), formulaire.getPlaque(), formulaire.getMarque(),
                    formulaire.getModele(), formulaire.getMotorisation(),
                    formulaire.getAnnee(), formulaire.getKilometrage());
        } catch (RegleMetierException e) {
            erreurs.addError(new FieldError("formulaire", "plaque", e.getMessage()));
            return retourFormulaire(modele, true);
        }

        redirection.addFlashAttribute("message", "Le véhicule a été ajouté.");
        return "redirect:/mes-vehicules";
    }

    @GetMapping("/{reference}/modifier")
    public String afficherModification(@AuthenticationPrincipal UserDetails membre,
                                       @PathVariable UUID reference,
                                       Model modele) {
        VehiculeVue vehicule = service.vue(reference, membre.getUsername());

        VehiculeForm formulaire = new VehiculeForm();
        formulaire.setPlaque(vehicule.plaque());
        formulaire.setMarque(vehicule.marque());
        formulaire.setModele(vehicule.modele());
        formulaire.setMotorisation(Motorisation.valueOf(vehicule.motorisation()));
        formulaire.setAnnee(vehicule.annee());
        formulaire.setKilometrage(vehicule.kilometrage());
        formulaire.setNumeroChassis(vehicule.numeroChassis());

        modele.addAttribute("titre", "Modifier " + vehicule.designation());
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("motorisations", Motorisation.values());
        modele.addAttribute("reference", reference);
        modele.addAttribute("modeAjout", false);
        return "reservation/vehicule-formulaire";
    }

    @PostMapping("/{reference}/modifier")
    public String modifier(@AuthenticationPrincipal UserDetails membre,
                           @PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") VehiculeForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            modele.addAttribute("reference", reference);
            return retourFormulaire(modele, false);
        }

        service.modifier(reference, membre.getUsername(), formulaire.getMarque(),
                formulaire.getModele(), formulaire.getMotorisation(),
                formulaire.getAnnee(), formulaire.getNumeroChassis());

        if (formulaire.getKilometrage() != null) {
            try {
                service.releverKilometrage(reference, membre.getUsername(), formulaire.getKilometrage());
            } catch (IllegalArgumentException e) {
                erreurs.addError(new FieldError("formulaire", "kilometrage", e.getMessage()));
                modele.addAttribute("reference", reference);
                return retourFormulaire(modele, false);
            }
        }

        redirection.addFlashAttribute("message", "Le véhicule a été mis à jour.");
        return "redirect:/mes-vehicules";
    }

    @PostMapping("/{reference}/supprimer")
    public String supprimer(@AuthenticationPrincipal UserDetails membre,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        service.supprimer(reference, membre.getUsername());
        redirection.addFlashAttribute("message", "Le véhicule a été retiré de votre parc.");
        return "redirect:/mes-vehicules";
    }

    private String retourFormulaire(Model modele, boolean modeAjout) {
        modele.addAttribute("titre", modeAjout ? "Ajouter un véhicule" : "Modifier le véhicule");
        modele.addAttribute("motorisations", Motorisation.values());
        modele.addAttribute("modeAjout", modeAjout);
        return "reservation/vehicule-formulaire";
    }
}