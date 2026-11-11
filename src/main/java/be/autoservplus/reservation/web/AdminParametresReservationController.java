package be.autoservplus.reservation.web;

import be.autoservplus.reservation.service.ParametresReservationService;
import be.autoservplus.reservation.service.dto.ParametresReservation;
import be.autoservplus.reservation.web.dto.ParametresReservationForm;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Paramètres de réservation sous /admin/planning/parametres, dans la section Planning. */
@Controller
@RequestMapping("/admin/planning/parametres")
public class AdminParametresReservationController {

    private static final String VUE = "admin/planning/parametres";

    private final ParametresReservationService service;
    private final MessageSource messages;

    public AdminParametresReservationController(ParametresReservationService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String afficher(Model modele) {
        ParametresReservation p = service.courants();
        ParametresReservationForm formulaire = new ParametresReservationForm();
        formulaire.setConfirmationAutomatique(p.confirmationAutomatique());
        formulaire.setDelaiReponseHeures(p.delaiReponseHeures());
        formulaire.setDelaiMinimalHeures(p.delaiMinimalHeures());
        formulaire.setDelaiAnnulationHeures(p.delaiAnnulationHeures());
        formulaire.setDelaiRappelHeures(p.delaiRappelHeures());
        modele.addAttribute("formulaire", formulaire);
        return VUE;
    }

    @PostMapping
    public String modifier(@Valid @ModelAttribute("formulaire") ParametresReservationForm formulaire,
                           BindingResult erreurs,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return VUE;
        }
        try {
            service.modifier(new ParametresReservation(formulaire.isConfirmationAutomatique(),
                    formulaire.getDelaiReponseHeures(), formulaire.getDelaiMinimalHeures(),
                    formulaire.getDelaiAnnulationHeures(), formulaire.getDelaiRappelHeures()));
        } catch (IllegalArgumentException e) {
            // Bornes de l'entité, atteintes seulement si elles divergent du formulaire.
            erreurs.reject("admin.parametres.validation.refus");
            return VUE;
        }
        redirection.addFlashAttribute("message",
                messages.getMessage("admin.parametres.message.enregistre", null, LocaleContextHolder.getLocale()));
        return "redirect:/admin/planning/parametres";
    }
}
