package be.autoservplus.pilotage.web;

import be.autoservplus.pilotage.service.TableauDeBordService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tableau de bord du gerant (BL-1), a la racine du back-office.
 *
 * <p>Servi sur {@code /admin} : c est la premiere page que voit le garage, et elle
 * donne acces aux autres ecrans d administration, qui n avaient jusqu ici aucun point
 * d entree commun.</p>
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link TableauDeBordService} redouble par {@code @PreAuthorize} en defense en
 * profondeur. <b>Aucune logique ici</b> : le controleur passe la locale et rend la vue,
 * tout le calcul appartient au service.</p>
 */
@Controller
public class TableauDeBordController {

    private final TableauDeBordService tableauDeBord;
    private final MessageSource messages;

    public TableauDeBordController(TableauDeBordService tableauDeBord, MessageSource messages) {
        this.tableauDeBord = tableauDeBord;
        this.messages = messages;
    }

    @GetMapping("/admin")
    public String afficher(Model modele) {
        modele.addAttribute("titre", messages.getMessage("admin.tableau.titre", null,
                LocaleContextHolder.getLocale()));
        modele.addAttribute("bord", tableauDeBord.duMois(LocaleContextHolder.getLocale()));
        return "admin/tableau-de-bord";
    }
}
