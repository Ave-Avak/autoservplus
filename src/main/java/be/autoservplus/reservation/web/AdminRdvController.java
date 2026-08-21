package be.autoservplus.reservation.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.service.AdminRdvService;
import be.autoservplus.reservation.web.dto.MotifForm;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Actions administratives sur les rendez-vous depuis l espace garage.
 *
 * <p>La protection d URL {@code /admin/**} de {@code SecuriteConfig} filtre le role
 * ADMINISTRATEUR ; {@code AdminRdvService} redouble par {@code @PreAuthorize} en
 * defense en profondeur. Le controleur ne duplique pas l annotation : le pattern
 * projet n en met pas sur les controleurs (verifie sur RdvController, VehiculeController).</p>
 *
 * <p>Les cinq transitions suivent le pattern PRG : POST -> flash message/erreur ->
 * redirect vers la liste. Le refus et l annulation par le garage exigent un motif :
 * ils s appuient sur une page dediee avec un formulaire, plutot que sur un textarea
 * inline dans la liste, pour rester lisibles et testables independamment.</p>
 */
@Controller
@RequestMapping("/admin/rendez-vous")
public class AdminRdvController {

    private static final String LISTE = "redirect:/admin/rendez-vous";

    private final AdminRdvService adminRdvs;

    public AdminRdvController(AdminRdvService adminRdvs) {
        this.adminRdvs = adminRdvs;
    }

    // --- liste ------------------------------------------------------------------------

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", "Rendez-vous à traiter");
        modele.addAttribute("demandesEnAttente", adminRdvs.demandesEnAttente());
        modele.addAttribute("aTraiterApresRdv", adminRdvs.aTraiterApresRdv());
        return "admin/rendez-vous";
    }

    // --- transitions sans motif -------------------------------------------------------

    @PostMapping("/{reference}/confirmer")
    public String confirmer(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> adminRdvs.confirmer(reference),
                numero -> "Le rendez-vous " + numero + " a été confirmé.");
    }

    @PostMapping("/{reference}/honorer")
    public String honorer(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> adminRdvs.marquerHonore(reference),
                numero -> "Le rendez-vous " + numero + " a été marqué comme honoré.");
    }

    @PostMapping("/{reference}/absent")
    public String absent(@AuthenticationPrincipal UserDetails admin,
                         @PathVariable UUID reference,
                         RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> adminRdvs.marquerAbsent(reference),
                numero -> "Le rendez-vous " + numero + " a été marqué comme non présenté.");
    }

    // --- refus (motif obligatoire) ----------------------------------------------------

    @GetMapping("/{reference}/refuser")
    public String afficherRefuser(@PathVariable UUID reference, Model modele) {
        return preparerFormulaireMotif(reference, new MotifForm(), modele,
                "Refuser le rendez-vous", "admin/refuser");
    }

    @PostMapping("/{reference}/refuser")
    public String refuser(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          @Valid @ModelAttribute("formulaire") MotifForm formulaire,
                          BindingResult erreurs,
                          Model modele,
                          RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            return preparerFormulaireMotif(reference, formulaire, modele,
                    "Refuser le rendez-vous", "admin/refuser");
        }
        return appliquerTransitionAvecMotif(reference, redirection,
                motif -> adminRdvs.refuser(reference, motif),
                formulaire.getMotif(),
                numero -> "Le rendez-vous " + numero + " a été refusé.");
    }

    // --- annulation par le garage (motif obligatoire) ---------------------------------

    @GetMapping("/{reference}/annuler")
    public String afficherAnnuler(@PathVariable UUID reference, Model modele) {
        return preparerFormulaireMotif(reference, new MotifForm(), modele,
                "Annuler le rendez-vous", "admin/annuler");
    }

    @PostMapping("/{reference}/annuler")
    public String annuler(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          @Valid @ModelAttribute("formulaire") MotifForm formulaire,
                          BindingResult erreurs,
                          Model modele,
                          RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            return preparerFormulaireMotif(reference, formulaire, modele,
                    "Annuler le rendez-vous", "admin/annuler");
        }
        return appliquerTransitionAvecMotif(reference, redirection,
                motif -> adminRdvs.annulerParLeGarage(reference, motif),
                formulaire.getMotif(),
                numero -> "Le rendez-vous " + numero + " a été annulé par le garage.");
    }

    // --- helpers privees --------------------------------------------------------------

    private String preparerFormulaireMotif(UUID reference, MotifForm formulaire,
                                           Model modele, String titre, String vue) {
        try {
            modele.addAttribute("titre", titre);
            modele.addAttribute("rdv", adminRdvs.vue(reference));
            modele.addAttribute("formulaire", formulaire);
            return vue;
        } catch (RessourceIntrouvableException e) {
            modele.addAttribute("erreur", e.getMessage());
            return "admin/rendez-vous-introuvable";
        }
    }

    private String appliquerTransition(UUID reference, RedirectAttributes redirection,
                                       java.util.function.Supplier<Rdv> action,
                                       java.util.function.Function<String, String> messageSuccesPourNumero) {
        try {
            Rdv rdv = action.get();
            redirection.addFlashAttribute("message", messageSuccesPourNumero.apply(rdv.getNumero()));
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            // machine a etats du domaine : transition interdite (RM-10)
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return LISTE;
    }

    private String appliquerTransitionAvecMotif(UUID reference, RedirectAttributes redirection,
                                                java.util.function.Function<String, Rdv> action,
                                                String motif,
                                                java.util.function.Function<String, String> messageSuccesPourNumero) {
        try {
            Rdv rdv = action.apply(motif);
            redirection.addFlashAttribute("message", messageSuccesPourNumero.apply(rdv.getNumero()));
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return LISTE;
    }
}
