package be.autoservplus.catalogue.web;

import be.autoservplus.avis.service.AvisService;
import be.autoservplus.common.web.FormulairePublic;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.catalogue.domain.Rayon;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
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
    private final MessageSource messages;
    private final IdentiteGarage garage;

    public CatalogueController(CatalogueService service, AvisService avis,
                               GalerieService galerie, MessageSource messages, IdentiteGarage garage) {
        this.service = service;
        this.avis = avis;
        this.galerie = galerie;
        this.messages = messages;
        this.garage = garage;
    }

    @GetMapping("/services")
    public String listerPrestations(@RequestParam(required = false) String categorie, Model modele) {
        List<ArticleVue> toutes = (categorie == null || categorie.isBlank())
                ? service.prestationsActives()
                : service.prestationsDeCategorie(categorie);
        // Une prestation à l'heure n'a ni durée ni prix de travail fini : dans la grille, sa
        // carte ne ressemblait à aucune autre. Son taux s'annonce sur une ligne au-dessus, la
        // prestation reste la même au back-office et sur le devis.
        List<ArticleVue> articles = toutes.stream().filter(article -> !article.aLHeure()).toList();
        modele.addAttribute("articles", articles);
        modele.addAttribute("tauxHoraires", toutes.stream().filter(ArticleVue::aLHeure).toList());
        modele.addAttribute("vignettes", galerie.vignettesDePrestations(references(articles)));
        // Note sur la carte à partir de trois avis publiés, jamais sur les produits : un avis
        // porte sur une intervention, donc sur une prestation.
        modele.addAttribute("notes", avis.notesAffichables(references(articles)));
        modele.addAttribute("categories", service.categoriesDePrestations());
        modele.addAttribute("categorieActive", categorie);
        modele.addAttribute("typeArticle", "service");
        modele.addAttribute("cleTitre", "catalogue.titre.services");
        modele.addAttribute("cleMeta", "catalogue.meta.services");
        return "catalogue/liste";
    }

    /**
     * Ancienne adresse d'un catalogue unique. Redirection temporaire : le catalogue peut
     * recevoir sa propre page d'accueil, qu'une redirection permanente gardée en cache
     * par les navigateurs masquerait.
     */
    @GetMapping("/catalogue")
    public String catalogue() {
        return "redirect:/services";
    }

    @GetMapping("/pieces")
    public String listerPieces(@RequestParam(required = false) String categorie, Model modele) {
        return listerRayon(Rayon.PIECES_AUTO, categorie, modele);
    }

    @GetMapping("/accessoires")
    public String listerAccessoires(@RequestParam(required = false) String categorie, Model modele) {
        return listerRayon(Rayon.ACCESSOIRES, categorie, modele);
    }

    @GetMapping("/entretien")
    public String listerEntretien(@RequestParam(required = false) String categorie, Model modele) {
        return listerRayon(Rayon.ENTRETIEN, categorie, modele);
    }

    private String listerRayon(Rayon rayon, String categorie, Model modele) {
        List<ArticleVue> articles = service.produitsDuRayon(rayon, categorie);
        modele.addAttribute("articles", articles);
        modele.addAttribute("vignettes", galerie.vignettesDePieces(references(articles)));
        modele.addAttribute("categories", service.categoriesDuRayon(rayon));
        modele.addAttribute("categorieActive", categorie);
        modele.addAttribute("typeArticle", "piece");
        modele.addAttribute("rayon", rayon);
        modele.addAttribute("cleTitre", "catalogue.titre." + rayon.prefixe());
        modele.addAttribute("cleMeta", "catalogue.meta." + rayon.prefixe());
        return "catalogue/liste";
    }

    private static List<UUID> references(List<ArticleVue> articles) {
        return articles.stream().map(ArticleVue::reference).toList();
    }

    @GetMapping("/services/{reference}")
    public String detailPrestation(@PathVariable UUID reference, Model modele) {
        ArticleVue article = service.vuePrestation(reference);
        modele.addAttribute("titre", article.libelle());
        modele.addAttribute("article", article);
        modele.addAttribute("typeArticle", "service");
        modele.addAttribute("donneesStructurees", DonneesStructurees.dePrestation(article,
                garage.pour(LocaleContextHolder.getLocale())));
        // BL-4 : la note moyenne et les avis publies font partie de la fiche.
        // Les pieces n en ont pas : un avis porte sur un travail effectue, pas sur
        // un article de stock.
        modele.addAttribute("syntheseAvis", avis.synthese(reference));
        modele.addAttribute("avis", avis.publiesPour(reference));
        // BL-9 : illustrations de la prestation.
        modele.addAttribute("photos", galerie.dePrestation(reference));
        return "catalogue/detail";
    }

    @FormulairePublic
    @GetMapping("/pieces/{reference}")
    public ModelAndView detailPiece(@PathVariable UUID reference, Model modele) {
        return detailProduit(Rayon.PIECES_AUTO, reference, modele);
    }

    @FormulairePublic
    @GetMapping("/accessoires/{reference}")
    public ModelAndView detailAccessoire(@PathVariable UUID reference, Model modele) {
        return detailProduit(Rayon.ACCESSOIRES, reference, modele);
    }

    @FormulairePublic
    @GetMapping("/entretien/{reference}")
    public ModelAndView detailProduitDEntretien(@PathVariable UUID reference, Model modele) {
        return detailProduit(Rayon.ENTRETIEN, reference, modele);
    }

    /**
     * Fiche d'un produit sous le préfixe de son rayon. Sous un autre préfixe, l'adresse
     * répond par une redirection permanente vers la bonne : les liens déjà émis dans les
     * courriels, les PDF et les paniers pointent tous {@code /pieces/}, et un article peut
     * changer de rayon avec sa catégorie.
     */
    private ModelAndView detailProduit(Rayon demande, UUID reference, Model modele) {
        ArticleVue article = service.vuePiece(reference);
        if (article.rayon() != demande) {
            RedirectView bonneAdresse = new RedirectView(article.chemin(), true);
            bonneAdresse.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            return new ModelAndView(bonneAdresse);
        }
        String nomDuRayon = messages.getMessage(demande.cleLibelle(), null, LocaleContextHolder.getLocale());
        modele.addAttribute("titre", article.libelle());
        modele.addAttribute("article", article);
        modele.addAttribute("typeArticle", "piece");
        modele.addAttribute("rayon", demande);
        modele.addAttribute("donneesStructurees", DonneesStructurees.dePiece(article, nomDuRayon,
                garage.pour(LocaleContextHolder.getLocale())));
        // BL-9 : illustrations de la piece.
        modele.addAttribute("photos", galerie.illustrationsDePiece(reference));
        return new ModelAndView("catalogue/detail");
    }

    /**
     * Résultats de la recherche, en une section par onglet du catalogue.
     *
     * <p>Chaque section a son numéro de page, compté à partir de 1 dans l'adresse. Un
     * seul numéro pour toutes aurait fait avancer les prestations chaque fois que le
     * visiteur tourne la page des pièces. Les liens d'une section gardent la page des
     * autres.</p>
     */
    @GetMapping("/recherche")
    public String rechercher(@RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "1") int pagePrestations,
                             @RequestParam(defaultValue = "1") int pagePieces,
                             @RequestParam(defaultValue = "1") int pageAccessoires,
                             @RequestParam(defaultValue = "1") int pageEntretien,
                             Model modele) {

        String terme = (q == null || q.isBlank()) ? null : CatalogueService.termeBorne(q);
        List<Page<ArticleVue>> pages = List.of(
                service.rechercherPrestations(terme, pagePrestations - 1),
                service.rechercherProduits(terme, Rayon.PIECES_AUTO, pagePieces - 1),
                service.rechercherProduits(terme, Rayon.ACCESSOIRES, pageAccessoires - 1),
                service.rechercherProduits(terme, Rayon.ENTRETIEN, pageEntretien - 1));

        modele.addAttribute("terme", terme);
        modele.addAttribute("tropCourt", terme != null && service.termeTropCourt(terme));
        modele.addAttribute("minimum", CatalogueService.LONGUEUR_MINIMALE_RECHERCHE);
        modele.addAttribute("sections", SectionRecherche.toutes(terme, pages));
        modele.addAttribute("total", pages.stream().mapToLong(Page::getTotalElements).sum());
        return "catalogue/recherche";
    }
}
