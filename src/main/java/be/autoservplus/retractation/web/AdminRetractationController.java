package be.autoservplus.retractation.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.service.AdminRetractationService;
import be.autoservplus.retractation.web.dto.MotifDecisionForm;
import be.autoservplus.vente.service.PrestataireIndisponibleException;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
 * Traitement des demandes de retractation depuis l espace garage (F30, RM-23).
 *
 * <p>Calque sur {@code AdminRdvController} : la protection d URL {@code /admin/**}
 * de {@code SecuriteConfig} filtre le role ADMINISTRATEUR,
 * {@code AdminRetractationService} redouble par {@code @PreAuthorize} en defense en
 * profondeur, et le controleur ne duplique pas l annotation — patron du projet.</p>
 *
 * <p>Les deux decisions suivent PRG : POST, flash message ou erreur, redirection vers
 * la file. Le refus exige un motif et passe donc par une page dediee plutot que par
 * un champ inline dans la liste — lisible, et testable independamment.</p>
 *
 * <p><b>Validation sans page intermediaire</b>, contrairement au refus : la
 * validation est le geste attendu, le dossier a deja ete examine, et le bouton porte
 * son montant. C est le refus qui demande une justification ecrite, pas
 * l acceptation d un droit que le consommateur exerce sans avoir a se justifier.</p>
 */
@Controller
@RequestMapping("/admin/retractations")
public class AdminRetractationController {

    private static final String LISTE = "redirect:/admin/retractations";

    private final AdminRetractationService retractations;
    private final MessageSource messages;

    public AdminRetractationController(AdminRetractationService retractations,
                                       MessageSource messages) {
        this.retractations = retractations;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", msg("admin.retractations.titre"));
        modele.addAttribute("demandes", retractations.demandesEnAttente());
        return "admin/retractations";
    }

    @PostMapping("/{reference}/valider")
    public String valider(@AuthenticationPrincipal UserDetails administrateur,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        try {
            DemandeAnnulation demande = retractations.valider(reference, administrateur.getUsername());
            redirection.addFlashAttribute("message", msg("admin.retractations.validee",
                    demande.getCommande().getNumero(), demande.getAvoir().getNumero()));
        } catch (RessourceIntrouvableException | ConflitConcurrenceException
                 | RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (PrestataireIndisponibleException e) {
            // Le remboursement est appele DANS la transaction de validation : un refus
            // du prestataire annule tout — avoir, numero rendu au compteur, bascule de
            // la commande. L administrateur doit donc lire que rien n a ete fait et
            // qu il peut reessayer, pas une page d erreur qui laisserait croire a un
            // etat indetermine. Le message de l exception n est pas affiche : il peut
            // porter des details du prestataire.
            redirection.addFlashAttribute("erreur",
                    msg("admin.retractations.prestataire-indisponible"));
        } catch (IllegalStateException e) {
            // Machine a etats du domaine : la demande n est plus en attente.
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return LISTE;
    }

    @GetMapping("/{reference}/refuser")
    public String afficherRefus(@PathVariable UUID reference, Model modele) {
        return preparerRefus(reference, new MotifDecisionForm(), modele);
    }

    @PostMapping("/{reference}/refuser")
    public String refuser(@AuthenticationPrincipal UserDetails administrateur,
                          @PathVariable UUID reference,
                          @Valid @ModelAttribute("formulaire") MotifDecisionForm formulaire,
                          BindingResult erreurs,
                          Model modele,
                          RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerRefus(reference, formulaire, modele);
        }
        try {
            DemandeAnnulation demande = retractations.refuser(
                    reference, formulaire.getMotif(), administrateur.getUsername());
            redirection.addFlashAttribute("message", msg("admin.retractations.refusee",
                    demande.getCommande().getNumero()));
        } catch (RessourceIntrouvableException | ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return LISTE;
    }

    private String preparerRefus(UUID reference, MotifDecisionForm formulaire, Model modele) {
        try {
            modele.addAttribute("titre", msg("admin.retractations.refus.titre"));
            modele.addAttribute("demande", retractations.vue(reference));
            modele.addAttribute("formulaire", formulaire);
            return "admin/retractation-refuser";
        } catch (RessourceIntrouvableException e) {
            modele.addAttribute("erreur", e.getMessage());
            return "admin/retractation-introuvable";
        }
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
