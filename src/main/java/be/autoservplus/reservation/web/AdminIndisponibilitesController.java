package be.autoservplus.reservation.web;

import be.autoservplus.reservation.service.AdminPlanningService;
import be.autoservplus.reservation.service.dto.ConflitRdv;
import be.autoservplus.reservation.service.dto.IndisponibiliteVue;
import be.autoservplus.reservation.web.dto.IndisponibiliteForm;
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

import java.util.List;
import java.util.UUID;

/**
 * Indisponibilités de l'atelier ou d'un poste (A24) sous
 * /admin/planning/indisponibilites.
 *
 * <p>Une indisponibilité qui recouvre des rendez-vous déjà pris est enregistrée, et ces
 * rendez-vous sont affichés sur la liste pour un traitement manuel.</p>
 */
@Controller
@RequestMapping("/admin/planning/indisponibilites")
public class AdminIndisponibilitesController {

    private static final String LISTE = "redirect:/admin/planning/indisponibilites";
    private static final String FORMULAIRE = "admin/planning/indisponibilite-formulaire";

    private final AdminPlanningService service;
    private final MessageSource messages;

    public AdminIndisponibilitesController(AdminPlanningService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("indisponibilites", service.indisponibilites());
        return "admin/planning/indisponibilites";
    }

    @GetMapping("/nouvelle")
    public String afficherCreation(Model modele) {
        return preparerFormulaire(new IndisponibiliteForm(), null, modele);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("formulaire") IndisponibiliteForm formulaire,
                        BindingResult erreurs,
                        Model modele,
                        RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, null, modele);
        }
        try {
            List<ConflitRdv> conflits = service.creerIndisponibilite(formulaire.getPosteReference(),
                    formulaire.getDebut(), formulaire.getFin(), formulaire.getMotif());
            redirection.addFlashAttribute("message", msg("admin.planning.indisponibilite.message.creee"));
            redirection.addFlashAttribute("conflits", conflits);
            return LISTE;
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("fin", "admin.planning.validation.ordre-dates");
        }
        return preparerFormulaire(formulaire, null, modele);
    }

    @GetMapping("/{reference}/modifier")
    public String afficherModification(@PathVariable UUID reference, Model modele) {
        return preparerFormulaire(versFormulaire(service.indisponibilite(reference)), reference, modele);
    }

    @PostMapping("/{reference}/modifier")
    public String modifier(@PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") IndisponibiliteForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, reference, modele);
        }
        try {
            List<ConflitRdv> conflits = service.modifierIndisponibilite(reference, formulaire.getPosteReference(),
                    formulaire.getDebut(), formulaire.getFin(), formulaire.getMotif());
            redirection.addFlashAttribute("message", msg("admin.planning.indisponibilite.message.modifiee"));
            redirection.addFlashAttribute("conflits", conflits);
            return LISTE;
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("fin", "admin.planning.validation.ordre-dates");
        }
        return preparerFormulaire(formulaire, reference, modele);
    }

    @PostMapping("/{reference}/retirer")
    public String retirer(@PathVariable UUID reference,
                          @AuthenticationPrincipal UserDetails admin,
                          RedirectAttributes redirection) {
        service.retirerIndisponibilite(reference, admin.getUsername());
        redirection.addFlashAttribute("message", msg("admin.planning.indisponibilite.message.retiree"));
        return LISTE;
    }

    private String preparerFormulaire(IndisponibiliteForm formulaire, UUID reference, Model modele) {
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("edition", reference != null);
        modele.addAttribute("reference", reference);
        modele.addAttribute("postes", service.postes());
        return FORMULAIRE;
    }

    private static IndisponibiliteForm versFormulaire(IndisponibiliteVue vue) {
        IndisponibiliteForm f = new IndisponibiliteForm();
        f.setPosteReference(vue.posteReference());
        f.setDebut(vue.debut());
        f.setFin(vue.fin());
        f.setMotif(vue.motif());
        return f;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
