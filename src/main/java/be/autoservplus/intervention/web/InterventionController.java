package be.autoservplus.intervention.web;

import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

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
}
