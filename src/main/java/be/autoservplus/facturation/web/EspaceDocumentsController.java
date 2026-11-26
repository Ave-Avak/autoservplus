package be.autoservplus.facturation.web;

import be.autoservplus.facturation.service.EspaceDocumentsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Espace documents du membre. La route est couverte par la règle commune des pages
 * authentifiées, et le service ne lit que les documents du compte connecté.
 */
@Controller
@RequestMapping("/mes-documents")
public class EspaceDocumentsController {

    private final EspaceDocumentsService documents;

    public EspaceDocumentsController(EspaceDocumentsService documents) {
        this.documents = documents;
    }

    @GetMapping
    public String espace(@AuthenticationPrincipal UserDetails membre,
                         @RequestParam(required = false) Integer annee, Model modele) {
        modele.addAttribute("documents", documents.documentsDe(membre.getUsername(), annee));
        return "facturation/documents";
    }
}
