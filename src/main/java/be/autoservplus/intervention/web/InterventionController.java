package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.DemandeValidationVue;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Suivi de l intervention par le membre proprietaire, sous /mes-interventions.
 *
 * <p>La page complete est rendue par {@link #suivi}. Le fragment de statut,
 * rafraichi par polling HTMX toutes les 10 secondes, est rendu par
 * {@link #blocStatut} : meme methode de chargement, meme service,
 * meme controle d ownership.</p>
 */
@Controller
@RequestMapping("/mes-interventions")
public class InterventionController {

    private final InterventionService service;

    public InterventionController(InterventionService service) {
        this.service = service;
    }

    @GetMapping("/{reference}")
    public String suivi(@AuthenticationPrincipal UserDetails membre,
                        @PathVariable UUID reference,
                        Model modele) {
        InterventionVueMembre vue = service.interventionDuMembre(reference, membre.getUsername());
        modele.addAttribute("titre", "Suivi de l'intervention " + vue.numero());
        modele.addAttribute("intervention", vue);
        return "intervention/suivi";
    }

    /**
     * Fragment rafraichi par polling HTMX toutes les 10 secondes. Ne renvoie que le
     * bloc statut, injecte a la place de son homologue dans la page complete via
     * {@code hx-swap="outerHTML"}.
     */
    @GetMapping("/{reference}/statut")
    public String blocStatut(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable UUID reference,
                             Model modele) {
        InterventionVueMembre vue = service.interventionDuMembre(reference, membre.getUsername());
        modele.addAttribute("intervention", vue);
        return "intervention/suivi :: blocStatut";
    }

    /**
     * Resolution RDV -> intervention pour le lien « Suivre l intervention »
     * depuis la fiche RDV. Redirige vers /mes-interventions/{ref} si trouvee,
     * 404 sinon (via {@code RessourceIntrouvableException}).
     */
    @GetMapping("/depuis-rdv/{rdvReference}")
    public String depuisRdv(@AuthenticationPrincipal UserDetails membre,
                            @PathVariable UUID rdvReference) {
        UUID interventionRef = service.referenceParRdvDuMembre(rdvReference, membre.getUsername());
        return "redirect:/mes-interventions/" + interventionRef;
    }

    // --- RM-15 : accord du membre sur un depassement de devis ------------------------

    /**
     * Ecran de decision. L identite vient du contexte de securite, jamais de l URL :
     * la reference seule ne suffit pas a acceder au dossier d un autre membre.
     */
    @GetMapping("/{reference}/validation")
    public String validation(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable UUID reference,
                             Model modele) {
        DemandeValidationVue vue = service.demandeValidation(reference, membre.getUsername());
        modele.addAttribute("titre", "Validation des travaux — " + vue.numero());
        modele.addAttribute("demande", vue);
        return "intervention/validation";
    }

    @PostMapping("/{reference}/valider")
    public String valider(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return repondre(reference, redirection,
                () -> service.validerDepassement(reference, membre.getUsername()),
                "Merci, le garage a été prévenu de votre accord. Les travaux se poursuivent.");
    }

    @PostMapping("/{reference}/refuser")
    public String refuser(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return repondre(reference, redirection,
                () -> service.refuserDepassement(reference, membre.getUsername()),
                "Votre refus a été enregistré. Seuls les travaux prévus au devis initial seront réalisés.");
    }

    /**
     * Pattern PRG commun aux deux reponses. {@link IllegalStateException} couvre le
     * double envoi (le membre revient en arriere et re-soumet alors que la demande est
     * deja tranchee) : le domaine refuse, le membre recoit un message, pas une 500.
     *
     * <p>{@link RessourceIntrouvableException} n est volontairement PAS catchee : elle
     * doit remonter en 404, comme sur les GET. La rattraper en message d erreur ferait
     * repondre 302 a un membre qui vise le dossier d autrui — un canal qui distingue
     * « reference inexistante » de « reference existante mais pas a vous ».</p>
     */
    private String repondre(UUID reference, RedirectAttributes redirection,
                            Supplier<Intervention> action, String succes) {
        try {
            action.get();
            redirection.addFlashAttribute("message", succes);
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", "Cette demande a déjà été traitée.");
        }
        return "redirect:/mes-interventions/" + reference;
    }
}
