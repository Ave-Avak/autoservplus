package be.autoservplus.reservation.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.service.AdminRdvService;
import be.autoservplus.reservation.web.dto.MotifForm;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Actions administratives sur les rendez-vous depuis l espace garage.
 *
 * <p>La protection d URL {@code /admin/**} de {@code SecuriteConfig} filtre le role
 * ADMINISTRATEUR. {@code AdminRdvService} redouble par {@code @PreAuthorize} en
 * defense en profondeur. Le controleur ne duplique pas l annotation : le pattern
 * projet n en met pas sur les controleurs (verifie sur RdvController, VehiculeController).</p>
 *
 * <p>Les cinq transitions suivent le patron PRG : POST, message flash, puis
 * redirection. Confirmer, honorer et marquer absent reviennent à l'écran d'où part le
 * formulaire : la liste par défaut, le détail quand le champ {@code retour} vaut
 * {@code detail}. Toute autre valeur ramène à la liste, jamais à une adresse fournie
 * par la requête. Le refus et l'annulation par le garage exigent un motif : ils
 * s'appuient sur une page dédiée avec un formulaire, et aboutissent au détail, qui
 * montre le nouveau statut et le motif.</p>
 */
@Controller
@RequestMapping("/admin/rendez-vous")
public class AdminRdvController {

    private static final String LISTE = "redirect:/admin/rendez-vous";
    private static final String RETOUR_DETAIL = "detail";

    private final AdminRdvService adminRdvs;

    public AdminRdvController(AdminRdvService adminRdvs) {
        this.adminRdvs = adminRdvs;
    }

    // --- liste ------------------------------------------------------------------------

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", "Rendez-vous à traiter");
        modele.addAttribute("demandesEnAttente", adminRdvs.demandesEnAttente());
        modele.addAttribute("rendezVousDuJour", adminRdvs.rendezVousDuJour());
        modele.addAttribute("rendezVousPassesSansIssue", adminRdvs.rendezVousPassesSansIssue());
        return "admin/rendez-vous";
    }

    // --- agenda et detail -------------------------------------------------------------

    /** Dates au format ISO, celui que rend et relit un champ {@code type="date"}. */
    @GetMapping("/agenda")
    public String agenda(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                         Model modele) {
        modele.addAttribute("agenda", adminRdvs.agenda(du, au));
        return "admin/rendez-vous-agenda";
    }

    /** Référence inconnue : 404 par GestionnaireErreursGlobal, comme partout. */
    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        modele.addAttribute("rdv", adminRdvs.vue(reference));
        return "admin/rendez-vous-detail";
    }

    // --- transitions sans motif -------------------------------------------------------

    @PostMapping("/{reference}/confirmer")
    public String confirmer(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            @RequestParam(required = false) String retour,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, retour, redirection,
                () -> adminRdvs.confirmer(reference),
                numero -> "Le rendez-vous " + numero + " a été confirmé.");
    }

    @PostMapping("/{reference}/honorer")
    public String honorer(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          @RequestParam(required = false) String retour,
                          RedirectAttributes redirection) {
        return appliquerTransition(reference, retour, redirection,
                () -> adminRdvs.marquerHonore(reference),
                numero -> "Le rendez-vous " + numero + " a été marqué comme honoré.");
    }

    @PostMapping("/{reference}/absent")
    public String absent(@AuthenticationPrincipal UserDetails admin,
                         @PathVariable UUID reference,
                         @RequestParam(required = false) String retour,
                         RedirectAttributes redirection) {
        return appliquerTransition(reference, retour, redirection,
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

    /** Reference inconnue : 404 par GestionnaireErreursGlobal, comme partout. */
    private String preparerFormulaireMotif(UUID reference, MotifForm formulaire,
                                           Model modele, String titre, String vue) {
        modele.addAttribute("titre", titre);
        modele.addAttribute("rdv", adminRdvs.vue(reference));
        modele.addAttribute("formulaire", formulaire);
        return vue;
    }

    private String appliquerTransition(UUID reference, String retour, RedirectAttributes redirection,
                                       java.util.function.Supplier<Rdv> action,
                                       java.util.function.Function<String, String> messageSuccesPourNumero) {
        try {
            Rdv rdv = action.get();
            redirection.addFlashAttribute("message", messageSuccesPourNumero.apply(rdv.getNumero()));
        } catch (RessourceIntrouvableException | ConflitConcurrenceException
                | be.autoservplus.common.exception.RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            // machine a etats du domaine : transition interdite (RM-10)
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return RETOUR_DETAIL.equals(retour) ? versDetail(reference) : LISTE;
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
        return versDetail(reference);
    }

    private static String versDetail(UUID reference) {
        return "redirect:/admin/rendez-vous/" + reference;
    }
}
