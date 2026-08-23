package be.autoservplus.galerie.web;

import be.autoservplus.galerie.service.AdminGalerieService;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.stockage.service.FichierTropVolumineuxException;
import be.autoservplus.stockage.service.TypeFichierRefuseException;
import be.autoservplus.stockage.service.TypeMedia;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Gestion des galeries par le garage (BL-9), sous {@code /admin/galerie}.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link AdminGalerieService} redouble par {@code @PreAuthorize}. CSRF actif sur les
 * POST, y compris multipart.</p>
 *
 * <p>POST-redirect-flash : un rafraichissement apres depot ne doit pas renvoyer les
 * fichiers une seconde fois.</p>
 *
 * <p>Les deux exceptions de la fondation upload sont catchees et rendues en message :
 * un format refuse ou un fichier trop lourd sont des erreurs d utilisateur, pas des
 * pannes, et une page d erreur technique ferait perdre la saisie.</p>
 */
@Controller
@RequestMapping("/admin/galerie")
public class AdminGalerieController {

    private final AdminGalerieService admin;
    private final GalerieService galerie;
    private final MessageSource messages;

    public AdminGalerieController(AdminGalerieService admin, GalerieService galerie,
                                  MessageSource messages) {
        this.admin = admin;
        this.galerie = galerie;
        this.messages = messages;
    }

    @GetMapping("/{type}/{reference}")
    public String gerer(@PathVariable String type, @PathVariable UUID reference, Model modele) {
        modele.addAttribute("titre", msg("admin.galerie.titre"));
        modele.addAttribute("type", type);
        modele.addAttribute("reference", reference);
        modele.addAttribute("photos", photosDe(type, reference));
        modele.addAttribute("typesAdmis", TypeMedia.typesMimeAdmis());
        modele.addAttribute("libelleTypesAdmis", TypeMedia.libelleDesTypesAdmis());
        return "admin/galerie";
    }

    @PostMapping("/{type}/{reference}")
    public String ajouter(@PathVariable String type,
                          @PathVariable UUID reference,
                          @RequestParam("fichiers") List<MultipartFile> fichiers,
                          @RequestParam("texteAlt") String texteAlt,
                          RedirectAttributes redirection) {
        try {
            int ajoutees = switch (type) {
                case "prestations" -> admin.ajouterAPrestation(reference, fichiers, texteAlt);
                case "pieces" -> admin.ajouterAPiece(reference, fichiers, texteAlt);
                case "interventions" -> admin.ajouterAIntervention(reference, fichiers, texteAlt);
                default -> throw new IllegalArgumentException("Type de galerie inconnu : " + type);
            };
            redirection.addFlashAttribute("message", msg("admin.galerie.ajoutees", ajoutees));
        } catch (TypeFichierRefuseException | FichierTropVolumineuxException
                 | IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/admin/galerie/" + type + "/" + reference;
    }

    @PostMapping("/{type}/{reference}/{id}/supprimer")
    public String supprimer(@PathVariable String type,
                            @PathVariable UUID reference,
                            @PathVariable Long id,
                            RedirectAttributes redirection) {
        admin.supprimer(id);
        redirection.addFlashAttribute("message", msg("admin.galerie.supprimee"));
        return "redirect:/admin/galerie/" + type + "/" + reference;
    }

    private List<?> photosDe(String type, UUID reference) {
        return switch (type) {
            case "prestations" -> galerie.dePrestation(reference);
            case "pieces" -> galerie.dePiece(reference);
            case "interventions" -> galerie.dIntervention(reference);
            default -> List.of();
        };
    }

    private String msg(String cle, Object... args) {
        return messages.getMessage(cle, args, LocaleContextHolder.getLocale());
    }
}
