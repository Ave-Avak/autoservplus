package be.autoservplus.reservation.web;

import be.autoservplus.reservation.service.AdminPlanningService;
import be.autoservplus.reservation.service.RefusPlanning;
import be.autoservplus.reservation.service.dto.PosteVue;
import be.autoservplus.reservation.web.dto.PosteForm;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
 * Postes d'atelier (A24) sous /admin/planning/postes.
 *
 * <p>Le retrait suit RM-29, comme le catalogue : la page de confirmation ne propose
 * que la suppression définitive quand aucun rendez-vous ne référence le poste, que la
 * désactivation sinon. La désactivation réduit la capacité de l'atelier sans toucher
 * aux rendez-vous déjà pris, affichés ensuite comme conflits.</p>
 */
@Controller
@RequestMapping("/admin/planning/postes")
public class AdminPostesController {

    private static final String LISTE = "redirect:/admin/planning/postes";
    private static final String FORMULAIRE = "admin/planning/poste-formulaire";

    private final AdminPlanningService service;
    private final MessageSource messages;

    public AdminPostesController(AdminPlanningService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("postes", service.postes());
        return "admin/planning/postes";
    }

    @GetMapping("/nouveau")
    public String afficherCreation(Model modele) {
        return preparerFormulaire(new PosteForm(), null, modele);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("formulaire") PosteForm formulaire,
                        BindingResult erreurs,
                        Model modele,
                        RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, null, modele);
        }
        try {
            service.creerPoste(formulaire.getLibelle(), formulaire.getDescription(), formulaire.getOrdre());
            redirection.addFlashAttribute("message", msg("admin.planning.poste.message.cree", formulaire.getLibelle()));
            return LISTE;
        } catch (RefusPlanning e) {
            erreurs.rejectValue(e.getChamp(), e.getCleMessage());
        }
        return preparerFormulaire(formulaire, null, modele);
    }

    @GetMapping("/{reference}/modifier")
    public String afficherModification(@PathVariable UUID reference, Model modele) {
        return preparerFormulaire(versFormulaire(service.poste(reference)), reference, modele);
    }

    @PostMapping("/{reference}/modifier")
    public String modifier(@PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") PosteForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, reference, modele);
        }
        try {
            service.modifierPoste(reference, formulaire.getLibelle(), formulaire.getDescription(),
                    formulaire.getOrdre());
            redirection.addFlashAttribute("message", msg("admin.planning.poste.message.modifie", formulaire.getLibelle()));
            return LISTE;
        } catch (RefusPlanning e) {
            erreurs.rejectValue(e.getChamp(), e.getCleMessage());
        }
        return preparerFormulaire(formulaire, reference, modele);
    }

    /** Page de confirmation : le diagnostic RM-29 décide de l'action proposée. */
    @GetMapping("/{reference}/supprimer")
    public String afficherSuppression(@PathVariable UUID reference, Model modele) {
        modele.addAttribute("proposition", service.propositionRetraitPoste(reference));
        return "admin/planning/poste-supprimer";
    }

    @PostMapping("/{reference}/supprimer")
    public String supprimer(@PathVariable UUID reference, RedirectAttributes redirection) {
        String libelle = service.poste(reference).libelle();
        try {
            service.supprimerDefinitivementPoste(reference);
            redirection.addFlashAttribute("message", msg("admin.planning.poste.message.supprime", libelle));
        } catch (RefusPlanning e) {
            redirection.addFlashAttribute("erreur", msg(e.getCleMessage(), libelle));
        }
        return LISTE;
    }

    @PostMapping("/{reference}/desactiver")
    public String desactiver(@PathVariable UUID reference, RedirectAttributes redirection) {
        String libelle = service.poste(reference).libelle();
        redirection.addFlashAttribute("conflits", service.desactiverPoste(reference));
        redirection.addFlashAttribute("message", msg("admin.planning.poste.message.desactive", libelle));
        return LISTE;
    }

    @PostMapping("/{reference}/activer")
    public String activer(@PathVariable UUID reference, RedirectAttributes redirection) {
        String libelle = service.poste(reference).libelle();
        service.activerPoste(reference);
        redirection.addFlashAttribute("message", msg("admin.planning.poste.message.active", libelle));
        return LISTE;
    }

    private String preparerFormulaire(PosteForm formulaire, UUID reference, Model modele) {
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("edition", reference != null);
        modele.addAttribute("reference", reference);
        return FORMULAIRE;
    }

    private static PosteForm versFormulaire(PosteVue vue) {
        PosteForm f = new PosteForm();
        f.setLibelle(vue.libelle());
        f.setDescription(vue.description());
        f.setOrdre((int) vue.ordre());
        return f;
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
