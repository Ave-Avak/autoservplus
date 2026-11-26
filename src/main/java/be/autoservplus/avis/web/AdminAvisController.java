package be.autoservplus.avis.web;

import be.autoservplus.avis.service.AdminAvisService;
import be.autoservplus.avis.service.FiltreAvis;
import be.autoservplus.avis.service.dto.AvisVue;
import be.autoservplus.common.web.PagesBackOffice;
import be.autoservplus.common.web.PaginationVue;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
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
 * Ecran de moderation des avis sous {@code /admin/avis} (BL-4).
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR.
 * {@link AdminAvisService} redouble par {@code @PreAuthorize} en defense en
 * profondeur.</p>
 *
 * <p>Aucun {@code try/catch} : {@code RessourceIntrouvableException} porte
 * {@code @ResponseStatus(NOT_FOUND)}, une reference inconnue produit donc un 404 sans
 * code supplementaire.</p>
 */
@Controller
@RequestMapping("/admin/avis")
public class AdminAvisController {

    private static final String LISTE = "redirect:/admin/avis";

    private final AdminAvisService avis;
    private final MessageSource messages;
    private final PagesBackOffice pages;

    public AdminAvisController(AdminAvisService avis, MessageSource messages, PagesBackOffice pages) {
        this.avis = avis;
        this.messages = messages;
        this.pages = pages;
    }

    @GetMapping
    public String liste(@RequestParam(required = false) FiltreAvis etat,
                        @RequestParam(defaultValue = "1") int page,
                        HttpServletRequest requete, Model modele) {
        Page<AvisVue> resultats = avis.pourModeration(etat, pages.demande(page));
        modele.addAttribute("titre", msg("admin.avis.titre"));
        modele.addAttribute("avis", resultats.getContent());
        modele.addAttribute("pagination", PaginationVue.de(resultats, requete));
        modele.addAttribute("etat", etat);
        modele.addAttribute("etats", FiltreAvis.values());
        return "admin/avis";
    }

    @PostMapping("/{reference}/masquer")
    public String masquer(@PathVariable UUID reference, RedirectAttributes redirection) {
        avis.masquer(reference);
        redirection.addFlashAttribute("message", msg("admin.avis.masque"));
        return LISTE;
    }

    @PostMapping("/{reference}/publier")
    public String publier(@PathVariable UUID reference, RedirectAttributes redirection) {
        avis.publier(reference);
        redirection.addFlashAttribute("message", msg("admin.avis.publie"));
        return LISTE;
    }

    @PostMapping("/{reference}/signaler")
    public String signaler(@PathVariable UUID reference, RedirectAttributes redirection) {
        avis.signaler(reference);
        redirection.addFlashAttribute("message", msg("admin.avis.signale"));
        return LISTE;
    }

    @PostMapping("/{reference}/lever-signalement")
    public String leverLeSignalement(@PathVariable UUID reference, RedirectAttributes redirection) {
        avis.leverLeSignalement(reference);
        redirection.addFlashAttribute("message", msg("admin.avis.signalement-leve"));
        return LISTE;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
