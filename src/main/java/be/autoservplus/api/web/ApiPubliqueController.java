package be.autoservplus.api.web;

import be.autoservplus.api.web.dto.ArticlePublic;
import be.autoservplus.api.web.dto.GaragePublic;
import be.autoservplus.api.web.dto.Lien;
import be.autoservplus.api.web.dto.PagePublique;
import be.autoservplus.api.web.dto.PrestationPublique;
import be.autoservplus.catalogue.domain.Rayon;
import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.i18n.LanguesSupportees;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import be.autoservplus.common.web.NumeroPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * API REST publique en lecture seule (BL-8), sous {@code /api/v1}.
 *
 * <p><b>Trois endpoints, aucun verbe d ecriture.</b> Ce n est pas une restriction
 * provisoire : l API sert a faire connaitre l offre du garage a des tiers
 * (agregateurs, comparateurs, site vitrine), pas a piloter le systeme. Ouvrir une
 * ecriture demanderait une authentification, une gestion de quotas et un modele
 * d autorisation qui n existent pas, et la table {@code clef_api} du socle reste
 * volontairement inexploitee.</p>
 *
 * <p><b>Aucune authentification, donc aucune donnee privee.</b> Les deux ressources
 * exposees sont deja publiques sur le site : le catalogue des prestations et des
 * produits, et l identite commerciale du garage. Aucun membre, aucune commande, aucun rendez-vous
 * n est joignable par cette API, et les DTO lui sont propres pour qu un champ ajoute
 * a un ecran ne fuite pas ici par accident.</p>
 *
 * <p><b>DTO dedies, jamais d entite ni de vue Thymeleaf.</b> {@code ArticleVue} sert
 * les gabarits et peut changer avec un ecran. Le contrat d une API publique ne le
 * peut pas.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalogue public",
        description = "Offre commerciale du garage, en lecture seule et sans authentification.")
public class ApiPubliqueController {

    /** Taille par defaut, alignee sur la pagination du catalogue web. */
    private static final int TAILLE_DEFAUT = 20;

    /**
     * Plafond de taille de page. Sans borne, un client demandant 100 000 elements
     * ferait charger tout le catalogue en memoire : l API est publique et anonyme,
     * elle doit borner ce qu un appelant peut reclamer.
     */
    private static final int TAILLE_MAX = 100;

    private final CatalogueService catalogue;
    private final IdentiteGarage identite;

    public ApiPubliqueController(CatalogueService catalogue, IdentiteGarage identite) {
        this.catalogue = catalogue;
        this.identite = identite;
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
                .map(PrestationPublique::de)
                .toList();
        return ResponseEntity.ok()
                // Catalogue public et peu changeant : une minute de cache suffit a
                // absorber un pic sans servir un tarif perime.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .header(HttpHeaders.CONTENT_LANGUAGE, langue.getLanguage())
                .varyBy(HttpHeaders.ACCEPT_LANGUAGE)
                .body(paginer(toutes, page, taille));
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
                .map(ArticlePublic::de)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .header(HttpHeaders.CONTENT_LANGUAGE, langue.getLanguage())
                .varyBy(HttpHeaders.ACCEPT_LANGUAGE)
                .body(paginer(tous, page, taille));
    }

    @Operation(summary = "Liste les garages",
            description = "La V1 est mono-tenant : la collection contient un seul element. "
                    + "Le contrat est deja celui de la V2 multi-tenant.")
    @GetMapping("/garages")
    public ResponseEntity<PagePublique<GaragePublic>> garages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAILLE_DEFAUT) int taille) {

        List<GaragePublic> tous = List.of(GaragePublic.de(identite));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(paginer(tous, page, taille));
    }

    /**
     * Decoupe la liste et compose les liens de navigation.
     *
     * <p>Les parametres hors bornes sont <b>ramenes</b> plutot que refuses : une page
     * negative devient la premiere, une taille excessive est plafonnee. Une API
     * publique qui renvoie 400 sur un parametre corrigeable complique la vie de ses
     * clients sans rien proteger. Une page au-dela du dernier element rend, elle, une
     * liste vide : ce qui est la reponse juste, pas une erreur.</p>
     */
    private <T> PagePublique<T> paginer(List<T> tous, int page, int taille) {
        int tailleReelle = Math.clamp(taille, 1, TAILLE_MAX);
        int pageReelle = NumeroPage.borne(page, tailleReelle);
        long total = tous.size();
        int nombreDePages = (int) Math.ceil((double) total / tailleReelle);

        int debut = Math.min(pageReelle * tailleReelle, tous.size());
        int fin = Math.min(debut + tailleReelle, tous.size());
        List<T> contenu = tous.subList(debut, fin);

        return new PagePublique<>(contenu, pageReelle, tailleReelle, total, nombreDePages,
                liens(pageReelle, tailleReelle, nombreDePages));
    }

    private List<Lien> liens(int page, int taille, int nombreDePages) {
        List<Lien> liens = new ArrayList<>();
        liens.add(new Lien("self", url(page, taille)));
        if (page > 0) {
            liens.add(new Lien("prev", url(page - 1, taille)));
        }
        if (page + 1 < nombreDePages) {
            liens.add(new Lien("next", url(page + 1, taille)));
        }
        return liens;
    }

    /**
     * URL absolue de la requete courante, avec la pagination reecrite.
     *
     * <p>Construite depuis la requete et non depuis une base codee en dur : derriere un
     * proxy, seul l en-tete de transfert connait le domaine public
     * ({@code autoservplus.be}), et une URL fabriquee a la main y renverrait vers
     * l hote interne.</p>
     */
    private static String url(int page, int taille) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("taille", taille)
                .toUriString();
    }
}
