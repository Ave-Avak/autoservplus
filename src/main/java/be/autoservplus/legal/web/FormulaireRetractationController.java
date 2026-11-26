package be.autoservplus.legal.web;

import be.autoservplus.common.debit.Plafonne;
import be.autoservplus.legal.service.FormulaireRetractation;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Modèle de formulaire de rétractation, en page et en PDF, dans la langue de
 * l'interface (CDE, livre VI, annexe 2).
 *
 * <p>Public, comme les CGV qui l'annoncent : un consommateur doit pouvoir s'en servir
 * sans compte actif, par exemple après la suppression du sien, et le document n'est le
 * secret de personne. Il ne porte que l'identité du garage, jamais celle du membre.</p>
 */
@Controller
@RequestMapping("/retractation/formulaire")
public class FormulaireRetractationController {

    private final FormulaireRetractation formulaire;

    public FormulaireRetractationController(FormulaireRetractation formulaire) {
        this.formulaire = formulaire;
    }

    @GetMapping
    public String page(Model modele) {
        modele.addAttribute("formulaire", formulaire.formulaire(LocaleContextHolder.getLocale()));
        return "legal/formulaire-retractation";
    }

    @Plafonne("pdf")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf() {
        Locale langue = LocaleContextHolder.getLocale();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("formulaire-retractation-" + langue.getLanguage() + ".pdf",
                        StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(formulaire.pdf(langue));
    }
}
