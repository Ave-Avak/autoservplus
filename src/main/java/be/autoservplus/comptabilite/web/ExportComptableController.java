package be.autoservplus.comptabilite.web;

import be.autoservplus.comptabilite.service.ExportComptableService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Export comptable CSV (BL-3), sous {@code /admin/export}.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link ExportComptableService} redouble par {@code @PreAuthorize}.</p>
 *
 * <p><b>Que des {@code GET}</b> : un export est une lecture, et l URL doit pouvoir
 * etre remise en favori ou rejouee par le comptable. Aucun etat n est modifie, donc
 * rien a proteger par CSRF.</p>
 *
 * <p>Les dates sont liees en ISO explicite, jamais par {@code th:field} : le format
 * belge casserait la validation du champ {@code <input type="date">}, piege deja
 * documente sur les ecrans de reservation.</p>
 */
@Controller
@RequestMapping("/admin/export")
public class ExportComptableController {

    private final ExportComptableService export;
    private final MessageSource messages;

    public ExportComptableController(ExportComptableService export, MessageSource messages) {
        this.export = export;
        this.messages = messages;
    }

    @GetMapping
    public String formulaire(Model modele) {
        modele.addAttribute("titre", msg("admin.export.titre"));
        return "admin/export";
    }

    @GetMapping("/factures.csv")
    public ResponseEntity<byte[]> factures(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua) {
        return fichier(export.facturesEnCsv(depuis, jusqua),
                "factures-%s-%s.csv".formatted(depuis, jusqua));
    }

    @GetMapping("/commandes.csv")
    public ResponseEntity<byte[]> commandes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua) {
        return fichier(export.commandesEnCsv(depuis, jusqua),
                "commandes-%s-%s.csv".formatted(depuis, jusqua));
    }

    /**
     * {@code text/csv} en UTF-8 et {@code attachment} : le navigateur propose
     * l enregistrement au lieu d afficher le contenu dans la page.
     */
    private ResponseEntity<byte[]> fichier(String csv, String nom) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(nom, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Donnees nominatives de tous les clients : aucun cache partage ne doit
                // les conserver.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
