package be.autoservplus.avis.web;

import be.autoservplus.avis.service.AvisService;
import be.autoservplus.avis.web.dto.FormulaireAvis;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.intervention.domain.Intervention;
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
 * Depot d un avis par le membre sur son intervention terminee (BL-4).
 *
 * <p>La protection d URL de {@code SecuriteConfig} exige l authentification ;
 * {@link AvisService} redouble par {@code @PreAuthorize} et porte le controle de
 * propriete. L identite vient du contexte de securite, jamais de l URL — la reference
 * d intervention ne designe pas un titulaire, elle est verifiee contre lui.</p>
 *
 * <p>POST-redirect-flash : un rafraichissement apres depot ne doit pas rejouer la
 * requete, l unicite en base le refuserait de toute facon mais avec une erreur
 * technique au lieu d un message.</p>
 */
@Controller
@RequestMapping("/mes-avis")
public class AvisController {

    private final AvisService avis;
    private final MessageSource messages;

    public AvisController(AvisService avis, MessageSource messages) {
        this.avis = avis;
        this.messages = messages;
    }

    @GetMapping("/{reference}/nouveau")
    public String formulaire(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable UUID reference,
                             @ModelAttribute("formulaire") FormulaireAvis formulaire,
                             Model modele) {
        Intervention intervention = avis.interventionNotable(membre.getUsername(), reference);
        return preparer(modele, intervention, reference);
    }

    @PostMapping("/{reference}")
    public String deposer(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable UUID reference,
                          @Valid @ModelAttribute("formulaire") FormulaireAvis formulaire,
                          BindingResult resultat,
                          Model modele,
                          RedirectAttributes redirection) {
        if (resultat.hasErrors()) {
            return preparer(modele,
                    avis.interventionNotable(membre.getUsername(), reference), reference);
        }
        try {
            avis.deposer(membre.getUsername(), reference,
                    formulaire.getNote(), formulaire.getCommentaire());
        } catch (RegleMetierException e) {
            // Deja note, ou travaux non termines : cas metier legitime, pas une panne.
            redirection.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/mes-interventions/" + reference;
        }
        redirection.addFlashAttribute("message", msg("avis.depose"));
        return "redirect:/mes-interventions/" + reference;
    }

    private String preparer(Model modele, Intervention intervention, UUID reference) {
        modele.addAttribute("titre", msg("avis.formulaire.titre"));
        modele.addAttribute("numeroIntervention", intervention.getNumero());
        modele.addAttribute("referenceIntervention", reference);
        return "avis/formulaire";
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
