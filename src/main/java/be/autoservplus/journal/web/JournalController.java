package be.autoservplus.journal.web;

import be.autoservplus.journal.service.JournalService;
import be.autoservplus.journal.service.dto.EntreeJournal;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Journal d audit du garage (BL-7), sous {@code /admin/journal}.
 *
 * <p><b>Un seul verbe, {@code GET}.</b> L ecran est en consultation : il n existe
 * aucun POST, donc aucun chemin par lequel une entree serait creee, modifiee ou
 * effacee depuis l interface.</p>
 *
 * <p>Les dates sont liees en ISO explicite et non par {@code th:field} : le format
 * belge du champ {@code <input type="date">} casserait la validation navigateur, piege
 * deja documente sur les ecrans de reservation.</p>
 */
@Controller
@RequestMapping("/admin/journal")
public class JournalController {

    private final JournalService journal;
    private final MessageSource messages;

    public JournalController(JournalService journal, MessageSource messages) {
        this.journal = journal;
        this.messages = messages;
    }

    @GetMapping
    public String consulter(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String acteur,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua,
                            Model modele) {
        List<EntreeJournal> entrees = journal.rechercher(type, acteur, depuis, jusqua);

        modele.addAttribute("titre", messages.getMessage("admin.journal.titre", null,
                LocaleContextHolder.getLocale()));
        modele.addAttribute("entrees", entrees);
        modele.addAttribute("type", type);
        modele.addAttribute("acteur", acteur);
        modele.addAttribute("depuis", depuis);
        modele.addAttribute("jusqua", jusqua);
        // Le gabarit prévient quand la limite est atteinte : sans cela, une liste
        // tronquée se lirait comme un historique complet.
        modele.addAttribute("tronque", entrees.size() >= JournalService.LIMITE);
        modele.addAttribute("limite", JournalService.LIMITE);
        return "admin/journal";
    }
}
