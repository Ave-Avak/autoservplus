package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR.
 * {@link InterventionService} redouble par {@code @PreAuthorize} en defense
 * en profondeur. Pattern PRG + flash, catch des exceptions du service, comme
 * {@code AdminRdvController}.</p>
 */
@Controller
@RequestMapping("/admin/interventions")
public class AdminInterventionController {

    private final InterventionService service;
    private final MessageSource messages;

    public AdminInterventionController(InterventionService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("interventions", service.interventionsEnCours());
        return "admin/interventions";
    }

    /** Une intervention inconnue répond 404 par la page d'erreur commune, et non par un renvoi à la liste. */
    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        InterventionVueAdmin vue = service.vueAdmin(reference);
        modele.addAttribute("intervention", vue);
        modele.addAttribute("prestations", service.prestationsActives());
        return "admin/intervention-detail";
    }

    // --- transitions ----------------------------------------------------------------

    @PostMapping("/{reference}/demarrer")
    public String demarrer(@AuthenticationPrincipal UserDetails admin,
                           @PathVariable UUID reference,
                           RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.demarrer(reference), "admin.intervention.message.demarree");
    }

    @PostMapping("/{reference}/suspendre")
    public String suspendre(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.suspendre(reference), "admin.intervention.message.suspendue");
    }

    @PostMapping("/{reference}/reprendre")
    public String reprendre(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.reprendre(reference), "admin.intervention.message.reprise");
    }

    @PostMapping("/{reference}/terminer")
    public String terminer(@AuthenticationPrincipal UserDetails admin,
                           @PathVariable UUID reference,
                           RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.terminer(reference), "admin.intervention.message.terminee");
    }

    @PostMapping("/{reference}/annuler")
    public String annuler(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.annuler(reference), "admin.intervention.message.annulee");
    }

    // --- commentaire et lignes ------------------------------------------------------

    @PostMapping("/{reference}/commentaire")
    public String modifierCommentaire(@AuthenticationPrincipal UserDetails admin,
                                      @PathVariable UUID reference,
                                      @RequestParam(required = false) String commentaire,
                                      RedirectAttributes redirection) {
        try {
            service.modifierCommentaireAdmin(reference, commentaire);
            redirection.addFlashAttribute("message", message("admin.intervention.message.commentaire"));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    /** Note interne de l'atelier (A16), modifiable dans tous les statuts. */
    @PostMapping("/{reference}/note-interne")
    public String modifierNoteInterne(@PathVariable UUID reference,
                                      @RequestParam(required = false) String noteInterne,
                                      RedirectAttributes redirection) {
        try {
            service.modifierNoteInterne(reference, noteInterne);
            redirection.addFlashAttribute("message", message("admin.intervention.note.enregistree"));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.note.trop-longue",
                    Intervention.LONGUEUR_NOTE_INTERNE));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
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
            redirection.addFlashAttribute("message", message("admin.intervention.message.ligne-ajoutee"));
        } catch (RessourceIntrouvableException e) {
            // La prestation choisie dans la liste a disparu du catalogue entre-temps.
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.prestation"));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.quantite"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
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
            redirection.addFlashAttribute("message", message("admin.intervention.message.ligne-retiree"));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    // --- helpers privees ------------------------------------------------------------

    private String appliquerTransition(UUID reference, RedirectAttributes redirection,
                                       Supplier<Intervention> action, String cleSucces) {
        try {
            Intervention it = action.get();
            redirection.addFlashAttribute("message", message(cleSucces, it.getNumero()));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalStateException e) {
            // Machine à états du domaine : la phrase française reste aux journaux.
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    private String message(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
