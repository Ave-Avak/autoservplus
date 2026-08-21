package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ecrans d administration des interventions sous /admin/interventions.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link InterventionService} redouble par {@code @PreAuthorize} en defense
 * en profondeur. Pattern PRG + flash, catch des exceptions du service, comme
 * {@code AdminRdvController}.</p>
 */
@Controller
@RequestMapping("/admin/interventions")
public class AdminInterventionController {

    private static final String LISTE = "redirect:/admin/interventions";

    private final InterventionService service;

    public AdminInterventionController(InterventionService service) {
        this.service = service;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", "Interventions à traiter");
        modele.addAttribute("interventions", service.interventionsEnCours());
        return "admin/interventions";
    }

    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele, RedirectAttributes redirection) {
        try {
            InterventionVueAdmin vue = service.vueAdmin(reference);
            modele.addAttribute("titre", "Intervention " + vue.numero());
            modele.addAttribute("intervention", vue);
            modele.addAttribute("prestations", service.prestationsActives());
            return "admin/intervention-detail";
        } catch (RessourceIntrouvableException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
            return LISTE;
        }
    }

    // --- transitions ----------------------------------------------------------------

    @PostMapping("/{reference}/demarrer")
    public String demarrer(@AuthenticationPrincipal UserDetails admin,
                           @PathVariable UUID reference,
                           RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.demarrer(reference), "démarrée");
    }

    @PostMapping("/{reference}/pause")
    public String pause(@AuthenticationPrincipal UserDetails admin,
                        @PathVariable UUID reference,
                        RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.mettreEnPause(reference), "mise en pause");
    }

    @PostMapping("/{reference}/reprendre")
    public String reprendre(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.reprendre(reference), "reprise");
    }

    @PostMapping("/{reference}/terminer")
    public String terminer(@AuthenticationPrincipal UserDetails admin,
                           @PathVariable UUID reference,
                           RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.terminer(reference), "terminée");
    }

    // --- commentaire et lignes ------------------------------------------------------

    @PostMapping("/{reference}/commentaire")
    public String modifierCommentaire(@AuthenticationPrincipal UserDetails admin,
                                      @PathVariable UUID reference,
                                      @RequestParam(required = false) String commentaire,
                                      RedirectAttributes redirection) {
        try {
            service.modifierCommentaireAdmin(reference, commentaire);
            redirection.addFlashAttribute("message", "Commentaire mis à jour.");
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/admin/interventions/" + reference;
    }

    @PostMapping("/{reference}/lignes")
    public String ajouterLigne(@AuthenticationPrincipal UserDetails admin,
                               @PathVariable UUID reference,
                               @RequestParam UUID prestation,
                               @RequestParam short quantite,
                               RedirectAttributes redirection) {
        try {
            service.ajouterLigneMainOeuvre(reference, prestation, quantite);
            redirection.addFlashAttribute("message", "Ligne ajoutée.");
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/admin/interventions/" + reference;
    }

    @PostMapping("/{reference}/lignes/{ligneId}/supprimer")
    public String supprimerLigne(@AuthenticationPrincipal UserDetails admin,
                                 @PathVariable UUID reference,
                                 @PathVariable Long ligneId,
                                 RedirectAttributes redirection) {
        try {
            service.retirerLigne(reference, ligneId);
            redirection.addFlashAttribute("message", "Ligne retirée.");
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/admin/interventions/" + reference;
    }

    // --- helpers privees ------------------------------------------------------------

    private String appliquerTransition(UUID reference, RedirectAttributes redirection,
                                       Supplier<Intervention> action, String verbe) {
        try {
            Intervention it = action.get();
            redirection.addFlashAttribute("message",
                    "L'intervention " + it.getNumero() + " a été " + verbe + ".");
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/admin/interventions/" + reference;
    }
}
