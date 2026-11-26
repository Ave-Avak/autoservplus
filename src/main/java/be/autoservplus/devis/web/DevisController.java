package be.autoservplus.devis.web;

import be.autoservplus.common.debit.Plafonne;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.devis.service.DevisService;
import be.autoservplus.devis.service.dto.PdfDevis;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Devis du membre connecté, sous {@code /mes-devis} (F15). Le formulaire d'émission est
 * porté par le panier, d'où le devis est tiré : un refus y ramène, avec son message.
 */
@Controller
@RequestMapping("/mes-devis")
public class DevisController {

    private final DevisService service;
    private final MessageSource messages;

    public DevisController(DevisService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", msg("devis.titre"));
        modele.addAttribute("devis", service.devisDuMembre(membre.getUsername()));
        return "devis/liste";
    }

    @Plafonne("emission-devis")
    @PostMapping
    public String emettre(@AuthenticationPrincipal UserDetails membre,
                          @RequestParam UUID vehicule,
                          RedirectAttributes redirection) {
        try {
            String numero = service.emettre(membre.getUsername(), vehicule);
            redirection.addFlashAttribute("message", msg("devis.message.emis", numero));
            return "redirect:/mes-devis";
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage(), refus.getArguments()));
            return "redirect:/panier";
        }
    }

    @Plafonne("pdf")
    @GetMapping("/{reference}/pdf")
    public ResponseEntity<byte[]> telecharger(@AuthenticationPrincipal UserDetails membre,
                                              @PathVariable UUID reference) {
        PdfDevis document = service.pdf(reference, membre.getUsername());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.nomDeFichier(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Document nominatif : aucun cache partagé ne doit le conserver.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(document.contenu());
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
