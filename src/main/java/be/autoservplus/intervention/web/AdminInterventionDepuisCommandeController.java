package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.service.dto.CommandeAPlanifierVue;
import be.autoservplus.intervention.service.PlanificationCommandeService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Ouverture d un dossier d atelier depuis une commande de services payee (F12-b).
 *
 * <p>Ecran <b>minimal et volontairement separe</b> de {@code AdminInterventionController} :
 * celui-ci pilote le cycle de vie d une intervention existante, celui-ci en cree une
 * depuis une autre origine. Les melanger ferait grossir un controleur deja charge de
 * dix routes.</p>
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link InterventionService} redouble par {@code @PreAuthorize} de classe.
 * POST-redirect-flash : la creation est idempotente cote service, mais un
 * rafraichissement ne doit pas rejouer la requete.</p>
 */
@Controller
@RequestMapping("/admin/commandes-a-planifier")
public class AdminInterventionDepuisCommandeController {

    private static final String LISTE = "redirect:/admin/commandes-a-planifier";

    private final PlanificationCommandeService planification;
    private final InterventionService interventions;
    private final MessageSource messages;

    public AdminInterventionDepuisCommandeController(PlanificationCommandeService planification,
                                                     InterventionService interventions,
                                                     MessageSource messages) {
        this.planification = planification;
        this.interventions = interventions;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", msg("admin.planification.titre"));
        modele.addAttribute("commandes", planification.commandesAPlanifier());
        return "admin/commandes-a-planifier";
    }

    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        CommandeAPlanifierVue vue = planification.detail(reference);
        modele.addAttribute("titre", msg("admin.planification.detail.titre", vue.numero()));
        modele.addAttribute("commande", vue);
        return "admin/commande-a-planifier";
    }

    @PostMapping("/{reference}")
    public String planifier(@PathVariable UUID reference,
                            @RequestParam UUID vehicule,
                            RedirectAttributes redirection) {
        try {
            var creee = interventions.creerDepuisCommande(reference, vehicule);
            redirection.addFlashAttribute("message",
                    msg("admin.planification.creee", creee.getNumero()));
        } catch (RegleMetierException | RessourceIntrouvableException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/admin/commandes-a-planifier/" + reference;
        }
        return LISTE;
    }

    private String msg(String cle, Object... args) {
        return messages.getMessage(cle, args, LocaleContextHolder.getLocale());
    }
}
