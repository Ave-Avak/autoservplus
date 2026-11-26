package be.autoservplus.api.web;

import be.autoservplus.api.web.dto.ArticlePublic;
import be.autoservplus.api.web.dto.GaragePublic;
import be.autoservplus.api.web.dto.Lien;
import be.autoservplus.api.web.dto.PagePublique;
import be.autoservplus.api.web.dto.PrestationPublique;
import be.autoservplus.catalogue.domain.Rayon;
import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.web.NumeroPage;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.i18n.LanguesSupportees;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * API REST publique en lecture seule (BL-8), sous {@code /api/v1}.
 *
 * <p><b>Aucun verbe d'écriture.</b> L'API sert à faire connaître l'offre du garage à des
 * tiers (agrégateurs, comparateurs, site vitrine), pas à piloter le système.</p>
 *
 * <p><b>Aucune authentification, donc aucune donnée privée.</b> Les ressources exposées
 * sont déjà publiques sur le site : le catalogue des prestations et des produits, et
 * l'identité commerciale du garage. Aucun membre, aucune commande, aucun rendez-vous n'est
 * joignable par cette API, et les DTO lui sont propres pour qu'un champ ajouté à un écran
 * ne fuite pas ici par accident.</p>
 *
 * <p><b>Réponses au format HAL (F41).</b> Chaque ressource porte ses liens sous
 * {@code _links}, une collection range ses éléments sous {@code _embedded}. Un client part
 * de {@code /api/v1/garages} et découvre le reste en suivant les liens. Le type
 * {@code application/hal+json} est rendu par défaut, {@code application/json} sur demande
 * expresse, avec le même corps.</p>
 *
 * <p><b>Liens bâtis sur l'adresse publique configurée</b> ({@code autoservplus.url-publique}),
 * comme les courriels et l'export d'agenda, et jamais sur l'en-tête {@code Host} de la
 * requête. Les réponses sont en cache public : un hôte forgé par un appelant y serait
 * servi aux suivants, avec des liens vers la prise de rendez-vous d'un autre site.</p>
 */
@RestController
@RequestMapping(value = "/api/v1", produces = {ApiPubliqueController.HAL_JSON, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Catalogue public",
        description = "Offre commerciale du garage, en lecture seule et sans authentification.")
public class ApiPubliqueController {

    static final String HAL_JSON = "application/hal+json";

    /** Taille par défaut, alignée sur la pagination du catalogue web. */
    private static final int TAILLE_DEFAUT = 20;

    /**
     * Plafond de taille de page. Sans borne, un client demandant 100 000 éléments ferait
     * charger tout le catalogue en mémoire : l'API est publique et anonyme, elle doit
     * borner ce qu'un appelant peut réclamer.
     */
    private static final int TAILLE_MAX = 100;

    private final CatalogueService catalogue;
    private final IdentiteGarage identite;
    private final String base;

    public ApiPubliqueController(CatalogueService catalogue, IdentiteGarage identite,
                                 @Value("${autoservplus.url-publique}") String urlPublique) {
        this.catalogue = catalogue;
        this.identite = identite;
        this.base = urlPublique.endsWith("/") ? urlPublique.substring(0, urlPublique.length() - 1) : urlPublique;
    }

    @Operation(summary = "Liste les prestations proposées",
            description = "Prestations actives du garage, paginées. Aucune donnée de gestion "
                    + "(stock, marge, seuil) n'est exposée. Le libellé, la description et la "
                    + "catégorie sont rendus dans la langue négociée par Accept-Language, parmi "
                    + "fr, nl et en. Un texte sans traduction est rendu en français.")
    @ApiResponse(responseCode = "200", description = "Page de prestations, triée sur le libellé rendu",
            headers = {
                    @Header(name = HttpHeaders.CONTENT_LANGUAGE,
                            description = "Langue négociée : fr, nl ou en. Un texte sans traduction "
                                    + "reste en français dans une réponse nl ou en.",
                            schema = @Schema(type = "string", allowableValues = {"fr", "nl", "en"})),
                    @Header(name = HttpHeaders.VARY,
                            description = "Accept-Language : la réponse dépend de la langue demandée.",
                            schema = @Schema(type = "string"))})
    @GetMapping("/prestations")
    public ResponseEntity<PagePublique<PrestationPublique>> prestations(
            @Parameter(description = "Index de page, à partir de zéro")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Éléments par page, 100 au maximum")
            @RequestParam(defaultValue = "" + TAILLE_DEFAUT) int taille,
            @Parameter(description = "Langues préférées du client. Admises : fr, nl, en. "
                    + "Sans en-tête ou sans langue admise, le français.", example = "nl-BE, fr;q=0.8")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        // La langue vient de l'en-tête seul, jamais de la session ni de « ?lang= » : la
        // réponse est mise en cache par des intermédiaires, qui ne connaissent que
        // l'en-tête annoncé par Vary.
        Locale langue = LanguesSupportees.negociee(acceptLanguage);
        List<PrestationPublique> toutes = catalogue.prestationsActives(langue).stream()
                .map(this::prestation)
                .toList();
        return dansLaLangue(langue).body(paginer("prestations", toutes, page, taille));
    }

    @Operation(summary = "Décrit une prestation",
            description = "Prestation active, dans la langue négociée par Accept-Language. Une "
                    + "prestation retirée du catalogue répond 404, comme une référence inconnue.")
    @ApiResponse(responseCode = "200", description = "La prestation et ses liens")
    @ApiResponse(responseCode = "404", description = "Référence inconnue ou prestation retirée")
    @GetMapping("/prestations/{reference}")
    public ResponseEntity<PrestationPublique> prestation(
            @PathVariable UUID reference,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        Locale langue = LanguesSupportees.negociee(acceptLanguage);
        return dansLaLangue(langue).body(prestation(catalogue.prestationActive(reference, langue)));
    }

    @Operation(summary = "Liste les produits du catalogue",
            description = "Pièces auto, accessoires et produits d'entretien actifs, paginés, "
                    + "filtrables par rayon. La quantité en stock n'est pas exposée : "
                    + "« disponible » dit seulement si l'article peut être commandé. Textes rendus "
                    + "dans la langue négociée par Accept-Language, comme pour les prestations.")
    @ApiResponse(responseCode = "200", description = "Page de produits, triée sur le libellé rendu",
            headers = {
                    @Header(name = HttpHeaders.CONTENT_LANGUAGE,
                            description = "Langue négociée : fr, nl ou en.",
                            schema = @Schema(type = "string", allowableValues = {"fr", "nl", "en"})),
                    @Header(name = HttpHeaders.VARY,
                            description = "Accept-Language : la réponse dépend de la langue demandée.",
                            schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "400", description = "Rayon inconnu")
    @GetMapping("/articles")
    public ResponseEntity<PagePublique<ArticlePublic>> articles(
            @Parameter(description = "Rayon : PIECES_AUTO, ACCESSOIRES ou ENTRETIEN. Sans rayon, les trois.")
            @RequestParam(required = false) Rayon rayon,
            @Parameter(description = "Index de page, à partir de zéro")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Éléments par page, 100 au maximum")
            @RequestParam(defaultValue = "" + TAILLE_DEFAUT) int taille,
            @Parameter(description = "Langues préférées du client. Admises : fr, nl, en.",
                    example = "nl-BE, fr;q=0.8")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        Locale langue = LanguesSupportees.negociee(acceptLanguage);
        List<ArticlePublic> tous = catalogue.produitsActifs(langue, rayon).stream()
                .map(this::article)
                .toList();
        return dansLaLangue(langue).body(paginer("articles", tous, page, taille));
    }

    @Operation(summary = "Décrit un produit du catalogue",
            description = "Produit actif, dans la langue négociée par Accept-Language. Un produit "
                    + "retiré du catalogue répond 404, comme une référence inconnue.")
    @ApiResponse(responseCode = "200", description = "Le produit et ses liens")
    @ApiResponse(responseCode = "404", description = "Référence inconnue ou produit retiré")
    @GetMapping("/articles/{reference}")
    public ResponseEntity<ArticlePublic> article(
            @PathVariable UUID reference,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        Locale langue = LanguesSupportees.negociee(acceptLanguage);
        return dansLaLangue(langue).body(article(catalogue.produitActif(reference, langue)));
    }

    @Operation(summary = "Liste les garages",
            description = "La V1 est mono-tenant : la collection contient un seul élément. "
                    + "Le contrat est déjà celui de la V2 multi-tenant. Point d'entrée de l'API : "
                    + "les liens du garage mènent à son catalogue et à ses pages.")
    @GetMapping("/garages")
    public ResponseEntity<PagePublique<GaragePublic>> garages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAILLE_DEFAUT) int taille) {

        return ResponseEntity.ok()
                .cacheControl(cacheCourt())
                .body(paginer("garages", List.of(garage()), page, taille));
    }

    @Operation(summary = "Décrit un garage",
            description = "L'identifiant est le numéro d'entreprise, chiffres seuls.")
    @ApiResponse(responseCode = "200", description = "Le garage et ses liens")
    @ApiResponse(responseCode = "404", description = "Identifiant inconnu")
    @GetMapping("/garages/{identifiant}")
    public ResponseEntity<GaragePublic> garage(@PathVariable String identifiant) {
        if (!identifiant.equals(GaragePublic.identifiantDe(identite))) {
            throw new RessourceIntrouvableException("Garage", identifiant);
        }
        return ResponseEntity.ok().cacheControl(cacheCourt()).body(garage());
    }

    // --- ressources et leurs liens -----------------------------------------------------

    private PrestationPublique prestation(ArticleVue vue) {
        Map<String, Lien> liens = new LinkedHashMap<>();
        liens.put("self", Lien.versApi(api("/prestations/{reference}", vue.reference())));
        liens.put("collection", Lien.versApi(api("/prestations")));
        liens.put("garage", Lien.versApi(lienGarage()));
        liens.put("fiche", Lien.versPage(site(vue.chemin())));
        // Une prestation à l'heure ne se réserve pas en ligne : son prix est un taux, la
        // durée du travail n'est connue qu'à l'atelier.
        if (!vue.aLHeure()) {
            liens.put("disponibilites", Lien.versApi(UriComponentsBuilder.fromUriString(base)
                    .path("/api/v1/disponibilites")
                    .queryParam("prestation", vue.reference())
                    .toUriString()));
            liens.put("reservation", Lien.versPage(UriComponentsBuilder.fromUriString(base)
                    .path("/mes-rendez-vous/nouveau")
                    .queryParam("prestation", vue.reference())
                    .toUriString()));
        }
        return PrestationPublique.de(vue, liens);
    }

    private ArticlePublic article(ArticleVue vue) {
        Map<String, Lien> liens = new LinkedHashMap<>();
        liens.put("self", Lien.versApi(api("/articles/{reference}", vue.reference())));
        liens.put("collection", Lien.versApi(api("/articles")));
        liens.put("garage", Lien.versApi(lienGarage()));
        liens.put("fiche", Lien.versPage(site(vue.chemin())));
        return ArticlePublic.de(vue, liens);
    }

    private GaragePublic garage() {
        Map<String, Lien> liens = new LinkedHashMap<>();
        liens.put("self", Lien.versApi(lienGarage()));
        liens.put("collection", Lien.versApi(api("/garages")));
        liens.put("prestations", Lien.versApi(api("/prestations")));
        liens.put("articles", Lien.versApi(api("/articles")));
        liens.put("disponibilites", Lien.versApi(api("/disponibilites")));
        liens.put("site", Lien.versPage(site("/")));
        liens.put("contact", Lien.versPage(site("/contact")));
        liens.put("reservation", Lien.versPage(site("/mes-rendez-vous/nouveau")));
        return GaragePublic.de(identite, liens);
    }

    private String lienGarage() {
        return api("/garages/{identifiant}", GaragePublic.identifiantDe(identite));
    }

    // --- pagination -------------------------------------------------------------------

    /**
     * Découpe la liste et compose les liens de navigation.
     *
     * <p>Les paramètres hors bornes sont <b>ramenés</b> plutôt que refusés : une page
     * négative devient la première, une taille excessive est plafonnée. Une API publique
     * qui renvoie 400 sur un paramètre corrigeable complique la vie de ses clients sans
     * rien protéger. Une page au-delà du dernier élément rend une liste vide, ce qui est la
     * réponse juste, pas une erreur.</p>
     */
    private <T> PagePublique<T> paginer(String collection, List<T> tous, int page, int taille) {
        int tailleReelle = Math.clamp(taille, 1, TAILLE_MAX);
        int pageReelle = NumeroPage.borne(page, tailleReelle);
        long total = tous.size();
        int nombreDePages = (int) Math.ceil((double) total / tailleReelle);

        int debut = Math.min(pageReelle * tailleReelle, tous.size());
        int fin = Math.min(debut + tailleReelle, tous.size());
        List<T> contenu = tous.subList(debut, fin);

        return new PagePublique<>(navigation(pageReelle, tailleReelle, nombreDePages),
                Map.of(collection, contenu),
                new PagePublique.Pagination(pageReelle, tailleReelle, total, nombreDePages));
    }

    private Map<String, Lien> navigation(int page, int taille, int nombreDePages) {
        int derniere = Math.max(nombreDePages - 1, 0);
        Map<String, Lien> liens = new LinkedHashMap<>();
        liens.put("self", Lien.versApi(pageCourante(page, taille)));
        liens.put("first", Lien.versApi(pageCourante(0, taille)));
        liens.put("last", Lien.versApi(pageCourante(derniere, taille)));
        if (page > 0) {
            liens.put("prev", Lien.versApi(pageCourante(Math.min(page - 1, derniere), taille)));
        }
        if (page + 1 < nombreDePages) {
            liens.put("next", Lien.versApi(pageCourante(page + 1, taille)));
        }
        return liens;
    }

    // --- adresses ---------------------------------------------------------------------

    /**
     * Adresse de la requête courante sur la base publique, avec la pagination réécrite. Le
     * chemin et les autres paramètres (le rayon) viennent de la requête, l'hôte jamais.
     */
    private String pageCourante(int page, int taille) {
        UriComponents courante = ServletUriComponentsBuilder.fromCurrentRequest().build();
        return UriComponentsBuilder.fromUriString(base)
                .path(courante.getPath())
                .query(courante.getQuery())
                .replaceQueryParam("page", page)
                .replaceQueryParam("taille", taille)
                .build()
                .toUriString();
    }

    private String api(String chemin, Object... variables) {
        return UriComponentsBuilder.fromUriString(base)
                .path("/api/v1").path(chemin)
                .buildAndExpand(variables)
                .toUriString();
    }

    private String site(String chemin) {
        return UriComponentsBuilder.fromUriString(base).path(chemin).toUriString();
    }

    // --- en-têtes ---------------------------------------------------------------------

    private static ResponseEntity.BodyBuilder dansLaLangue(Locale langue) {
        return ResponseEntity.ok()
                .cacheControl(cacheCourt())
                .header(HttpHeaders.CONTENT_LANGUAGE, langue.getLanguage())
                .varyBy(HttpHeaders.ACCEPT_LANGUAGE);
    }

    /** Catalogue public et peu changeant : une minute de cache absorbe un pic sans servir un tarif périmé. */
    private static CacheControl cacheCourt() {
        return CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();
    }
}
