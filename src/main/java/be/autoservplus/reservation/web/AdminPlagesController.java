package be.autoservplus.reservation.web;

import be.autoservplus.reservation.service.AdminPlanningService;
import be.autoservplus.reservation.service.RefusPlanning;
import be.autoservplus.reservation.service.dto.ConflitRdv;
import be.autoservplus.reservation.service.dto.PlageVue;
import be.autoservplus.reservation.web.dto.PlageForm;
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

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

/**
 * Plages d'ouverture du garage (A24) sous /admin/planning/plages.
 *
 * <p>Les plages n'ont pas de référence publique dans le schéma : l'adresse porte leur
 * identifiant numérique, comme les images de la galerie. L'écran est réservé au
 * back-office, qui voit de toute façon toutes les plages.</p>
 *
 * <p>Le chevauchement de deux plages est jugé par la base et revient sous le champ de
 * l'heure de début. Après une modification ou un retrait, les rendez-vous laissés hors
 * des heures d'ouverture sont affichés sur la liste, en attribut flash.</p>
 */
@Controller
@RequestMapping("/admin/planning/plages")
public class AdminPlagesController {

    private static final String LISTE = "redirect:/admin/planning/plages";
    private static final String FORMULAIRE = "admin/planning/plage-formulaire";

    private final AdminPlanningService service;
    private final MessageSource messages;

    public AdminPlagesController(AdminPlanningService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("plages", service.plages());
        return "admin/planning/plages";
    }

    @GetMapping("/nouvelle")
    public String afficherCreation(Model modele) {
        return preparerFormulaire(new PlageForm(), null, modele);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("formulaire") PlageForm formulaire,
                        BindingResult erreurs,
                        Model modele,
                        RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, null, modele);
        }
        try {
            service.creerPlage(DayOfWeek.of(formulaire.getJour()), formulaire.getHeureDebut(),
                    formulaire.getHeureFin(), formulaire.isActif());
            redirection.addFlashAttribute("message", msg("admin.planning.plage.message.creee"));
            return LISTE;
        } catch (RefusPlanning e) {
            erreurs.rejectValue(e.getChamp(), e.getCleMessage());
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("heureFin", "admin.planning.validation.ordre-heures");
        }
        return preparerFormulaire(formulaire, null, modele);
    }

    @GetMapping("/{id}/modifier")
    public String afficherModification(@PathVariable Long id, Model modele) {
        return preparerFormulaire(versFormulaire(service.plage(id)), id, modele);
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id,
                           @Valid @ModelAttribute("formulaire") PlageForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, id, modele);
        }
        try {
            List<ConflitRdv> conflits = service.modifierPlage(id, DayOfWeek.of(formulaire.getJour()),
                    formulaire.getHeureDebut(), formulaire.getHeureFin(), formulaire.isActif());
            redirection.addFlashAttribute("message", msg("admin.planning.plage.message.modifiee"));
            redirection.addFlashAttribute("conflits", conflits);
            return LISTE;
        } catch (RefusPlanning e) {
            erreurs.rejectValue(e.getChamp(), e.getCleMessage());
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("heureFin", "admin.planning.validation.ordre-heures");
        }
        return preparerFormulaire(formulaire, id, modele);
    }

    /** Confirmation avant retrait, comme pour un poste : le geste peut créer des conflits. */
    @GetMapping("/{id}/retirer")
    public String confirmerRetrait(@PathVariable Long id, Model modele) {
        modele.addAttribute("plage", service.plage(id));
        return "admin/planning/plage-retirer";
    }

    @PostMapping("/{id}/retirer")
    public String retirer(@PathVariable Long id,
                          @AuthenticationPrincipal UserDetails admin,
                          RedirectAttributes redirection) {
        List<ConflitRdv> conflits = service.retirerPlage(id, admin.getUsername());
        redirection.addFlashAttribute("message", msg("admin.planning.plage.message.retiree"));
        redirection.addFlashAttribute("conflits", conflits);
        return LISTE;
    }

    private String preparerFormulaire(PlageForm formulaire, Long id, Model modele) {
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("edition", id != null);
        modele.addAttribute("id", id);
        modele.addAttribute("jours", Arrays.stream(DayOfWeek.values())
                .map(j -> new JourChoix(j.getValue(), PlageVue.cleJour(j))).toList());
        return FORMULAIRE;
    }

    private static PlageForm versFormulaire(PlageVue vue) {
        PlageForm f = new PlageForm();
        f.setJour(vue.jour().getValue());
        f.setHeureDebut(vue.heureDebut());
        f.setHeureFin(vue.heureFin());
        f.setActif(vue.actif());
        return f;
    }

    /** Option de la liste des jours : numéro ISO et clé du libellé. */
    public record JourChoix(int numero, String cle) {
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
