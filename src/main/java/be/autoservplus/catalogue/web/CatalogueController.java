package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.web.dto.ArticleVue;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Consultation publique du catalogue.
 *
 * <p>Ces pages sont accessibles sans authentification et constituent le socle du
 * referencement naturel : leur contenu est present dans le HTML servi, sans execution
 * de code cote client.</p>
 */
@Controller
public class CatalogueController {

    private final CatalogueService service;

    public CatalogueController(CatalogueService service) {
        this.service = service;
    }

    @GetMapping("/services")
    public String listerPrestations(@RequestParam(required = false) String categorie,
                                    Model modele) {
        List<ArticleVue> articles = (categorie == null || categorie.isBlank())
                ? service.prestationsActives().stream().map(ArticleVue::de).toList()
                : service.prestationsDeCategorie(categorie).stream().map(ArticleVue::de).toList();

        modele.addAttribute("titre", "Nos prestations");
        modele.addAttribute("articles", articles);
        modele.addAttribute("categories", service.categoriesDePrestations());
        modele.addAttribute("categorieActive", categorie);
        modele.addAttribute("typeArticle", "service");
        return "catalogue/liste";
    }

    @GetMapping("/pieces")
    public String listerPieces(@RequestParam(required = false) String categorie, Model modele) {
        List<ArticleVue> articles = (categorie == null || categorie.isBlank())
                ? service.piecesActives().stream().map(ArticleVue::de).toList()
                : service.piecesDeCategorie(categorie).stream().map(ArticleVue::de).toList();

        modele.addAttribute("titre", "Pièces détachées");
        modele.addAttribute("articles", articles);
        modele.addAttribute("categories", service.categoriesDePieces());
        modele.addAttribute("categorieActive", categorie);
        modele.addAttribute("typeArticle", "piece");
        return "catalogue/liste";
    }

    @GetMapping("/services/{reference}")
    public String detailPrestation(@PathVariable UUID reference, Model modele) {
        ArticleVue article = ArticleVue.de(service.prestationParReference(reference));
        modele.addAttribute("titre", article.libelle());
        modele.addAttribute("article", article);
        modele.addAttribute("typeArticle", "service");
        return "catalogue/detail";
    }

    @GetMapping("/pieces/{reference}")
    public String detailPiece(@PathVariable UUID reference, Model modele) {
        ArticleVue article = ArticleVue.de(service.pieceParReference(reference));
        modele.addAttribute("titre", article.libelle());
        modele.addAttribute("article", article);
        modele.addAttribute("typeArticle", "piece");
        return "catalogue/detail";
    }

    @GetMapping("/recherche")
    public String rechercher(@RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "0") int page,
                             Model modele) {
        modele.addAttribute("titre", "Recherche");
        modele.addAttribute("terme", q);

        var resultatsPrestations = service.rechercherPrestations(q, page);
        var resultatsPieces = service.rechercherPieces(q, page);

        modele.addAttribute("prestations", resultatsPrestations.map(ArticleVue::de).getContent());
        modele.addAttribute("pieces", resultatsPieces.map(ArticleVue::de).getContent());
        modele.addAttribute("total",
                resultatsPrestations.getTotalElements() + resultatsPieces.getTotalElements());
        return "catalogue/recherche";
    }
}