package be.autoservplus.importcsv.web;

import be.autoservplus.importcsv.service.ImportCatalogueService;
import be.autoservplus.importcsv.service.ImportRefuseException;
import be.autoservplus.importcsv.service.dto.RapportImport;
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

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Import CSV du catalogue (BL-2), sous {@code /admin/import}.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link ImportCatalogueService} redouble par {@code @PreAuthorize}. CSRF actif sur le
 * POST multipart.</p>
 *
 * <p><b>Pas de POST-redirect-flash ici, contrairement au reste du projet.</b> Le
 * rapport d import peut compter des dizaines de lignes en erreur : le faire transiter
 * par un attribut flash le ferait perdre au moindre rafraichissement, au moment precis
 * ou le garage en a besoin pour corriger son fichier. La page est donc rendue
 * directement, et l import etant idempotent (trouve-ou-cree sur le code), un renvoi du
 * formulaire ne cree pas de doublon.</p>
 */
@Controller
@RequestMapping("/admin/import")
public class ImportCatalogueController {

    private static final String VUE = "admin/import";

    private final ImportCatalogueService imports;
    private final MessageSource messages;

    public ImportCatalogueController(ImportCatalogueService imports, MessageSource messages) {
        this.imports = imports;
        this.messages = messages;
    }

    @GetMapping
    public String formulaire(Model modele) {
        preparer(modele);
        return VUE;
    }

    @PostMapping("/{type}")
    public String importer(@PathVariable String type,
                           @RequestParam("fichier") MultipartFile fichier,
                           Model modele) {
        preparer(modele);
        modele.addAttribute("type", type);

        if (fichier == null || fichier.isEmpty()) {
            modele.addAttribute("erreur", msg("admin.import.fichier-vide"));
            return VUE;
        }
        try {
            byte[] contenu = fichier.getBytes();
            RapportImport rapport = "pieces".equals(type)
                    ? imports.importerPieces(contenu)
                    : imports.importerPrestations(contenu);
            modele.addAttribute("rapport", rapport);
        } catch (ImportRefuseException e) {
            // Import annule dans son entier : le rapport porte la liste de ce qui doit
            // etre corrige, c est lui qui est utile a l ecran.
            modele.addAttribute("rapport", e.rapport());
            modele.addAttribute("erreur", msg("admin.import.refuse"));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier importe impossible", e);
        }
        return VUE;
    }

    private void preparer(Model modele) {
        modele.addAttribute("titre", msg("admin.import.titre"));
        modele.addAttribute("entetePrestations", ImportCatalogueService.enTetePrestations());
        modele.addAttribute("entetePieces", ImportCatalogueService.enTetePieces());
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
