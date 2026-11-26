package be.autoservplus.facturation.web;

import be.autoservplus.common.debit.Plafonne;
import be.autoservplus.common.web.PeriodeFiltre;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.AdminFacturationService;
import be.autoservplus.facturation.service.PdfAvoirService;
import be.autoservplus.facturation.service.PdfFactureService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Factures et avoirs du back-office : listes par période et téléchargement.
 *
 * <p>Les en-têtes du téléchargement sont ceux du membre : pièce jointe, nom de fichier
 * en UTF-8, aucun cache partagé pour un document nominatif.</p>
 */
@Controller
@RequestMapping("/admin")
public class AdminFacturationController {

    private final AdminFacturationService service;
    private final PdfFactureService pdfFactures;
    private final PdfAvoirService pdfAvoirs;

    public AdminFacturationController(AdminFacturationService service,
                                      PdfFactureService pdfFactures, PdfAvoirService pdfAvoirs) {
        this.service = service;
        this.pdfFactures = pdfFactures;
        this.pdfAvoirs = pdfAvoirs;
    }

    @GetMapping("/factures")
    public String factures(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                           @RequestParam(required = false) String recherche,
                           @RequestParam(defaultValue = "1") int page,
                           Model modele) {
        PeriodeFiltre periode = service.periode(du, au);
        preparer(modele, periode, recherche);
        modele.addAttribute("resultats", service.factures(periode, recherche, page - 1));
        return "admin/factures";
    }

    @GetMapping("/avoirs")
    public String avoirs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                         @RequestParam(required = false) String recherche,
                         @RequestParam(defaultValue = "1") int page,
                         Model modele) {
        PeriodeFiltre periode = service.periode(du, au);
        preparer(modele, periode, recherche);
        modele.addAttribute("resultats", service.avoirs(periode, recherche, page - 1));
        return "admin/avoirs";
    }

    @Plafonne("pdf")
    @GetMapping("/factures/{reference}/pdf")
    public ResponseEntity<byte[]> facture(@PathVariable UUID reference) {
        Facture facture = service.facture(reference);
        return document(pdfFactures.pdfDe(facture), pdfFactures.nomDeFichier(facture));
    }

    @Plafonne("pdf")
    @GetMapping("/avoirs/{reference}/pdf")
    public ResponseEntity<byte[]> avoir(@PathVariable UUID reference) {
        Avoir avoir = service.avoir(reference);
        return document(pdfAvoirs.pdfDe(avoir), pdfAvoirs.nomDeFichier(avoir));
    }

    private static void preparer(Model modele, PeriodeFiltre periode, String recherche) {
        modele.addAttribute("periode", periode);
        modele.addAttribute("recherche", recherche);
    }

    private static ResponseEntity<byte[]> document(byte[] pdf, String nomDeFichier) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(nomDeFichier, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf);
    }
}
