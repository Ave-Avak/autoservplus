package be.autoservplus.catalogue.web;

import be.autoservplus.avis.service.AvisService;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Consultation publique du catalogue.
 *
 * <p>Ces pages sont accessibles sans authentification et constituent le socle du
 * referencement naturel : leur contenu figure dans le HTML servi, sans execution de code
 * cote client.</p>
 *
 * <p>Le controleur ne recoit que des objets de transfert, jamais d entites. La conversion
 * a lieu dans le service, a l interieur de la transaction, ce qui evite toute
 * initialisation paresseuse hors session.</p>
 */
@Controller
public class CatalogueController {

    private final CatalogueService service;
    private final AvisService avis;
    private final GalerieService galerie;

    public CatalogueController(CatalogueService service, AvisService avis,
                               GalerieService galerie) {
        this.service = service;
        this.avis = avis;
        this.galerie = galerie;
    }

    @GetMapping("/services")
    public String listerPrestations(@RequestParam(required = false) String categorie, Model modele) {
        modele.addAttribute("titre", "Nos prestations");
        modele.addAttribute("articles", (categorie == null || categorie.isBlank())
                ? service.prestationsActives()
                : service.prestationsDeCategorie(categorie));
        modele.addAttribute("categories", service.categoriesDePrestations());
        modele.addAttribute("categorieActive", categorie);
        modele.addAttribute("typeArticle", "service");
        return "catalogue/liste";
    }

    @GetMapping("/pieces")
    public String listerPieces(@RequestParam(required = false) String categorie, Model modele) {
        modele.addAttribute("titre", "Pièces détachées");
        modele.addAttribute("articles", (categorie == null || categorie.isBlank())
                ? service.piecesActives()
                : service.piecesDeCategorie(categorie));
        modele.addAttribute("categories", service.categoriesDePieces());
        modele.addAttribute("categorieActive", categorie);
        modele.addAttribute("typeArticle", "piece");
        return "catalogue/liste";
    }

    @GetMapping("/services/{reference}")
    public String detailPrestation(@PathVariable UUID reference, Model modele) {
        ArticleVue article = service.vuePrestation(reference);
        modele.addAttribute("titre", article.libelle());
        modele.addAttribute("article", article);
        modele.addAttribute("typeArticle", "service");
        // BL-4 : la note moyenne et les avis publies font partie de la fiche.
        // Les pieces n en ont pas — un avis porte sur un travail effectue, pas sur
        // un article de stock.
        modele.addAttribute("syntheseAvis", avis.synthese(reference));
        modele.addAttribute("avis", avis.publiesPour(reference));
        // BL-9 : illustrations de la prestation.
        modele.addAttribute("photos", galerie.dePrestation(reference));
        return "catalogue/detail";
    }

    @GetMapping("/pieces/{reference}")
    public String detailPiece(@PathVariable UUID reference, Model modele) {
        ArticleVue article = service.vuePiece(reference);
        modele.addAttribute("titre", article.libelle());
        modele.addAttribute("article", article);
        modele.addAttribute("typeArticle", "piece");
        // BL-9 : illustrations de la piece.
        modele.addAttribute("photos", galerie.dePiece(reference));
        return "catalogue/detail";
    }

    @GetMapping("/recherche")
    public String rechercher(@RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "0") int page,
                             Model modele) {

        var resultatsPrestations = service.rechercherPrestations(q, page);
        var resultatsPieces = service.rechercherPieces(q, page);

        modele.addAttribute("titre", "Recherche");
        modele.addAttribute("terme", q);
        modele.addAttribute("prestations", resultatsPrestations.getContent());
        modele.addAttribute("pieces", resultatsPieces.getContent());
        modele.addAttribute("total",
                resultatsPrestations.getTotalElements() + resultatsPieces.getTotalElements());
        return "catalogue/recherche";
    }
}