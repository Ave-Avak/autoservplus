package be.autoservplus.api.web;

import be.autoservplus.api.web.dto.GaragePublic;
import be.autoservplus.api.web.dto.Lien;
import be.autoservplus.api.web.dto.PagePublique;
import be.autoservplus.api.web.dto.PrestationPublique;
import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.config.IdentiteGarage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * API REST publique en lecture seule (BL-8), sous {@code /api/v1}.
 *
 * <p><b>Deux endpoints, aucun verbe d ecriture.</b> Ce n est pas une restriction
 * provisoire : l API sert a faire connaitre l offre du garage a des tiers
 * (agregateurs, comparateurs, site vitrine), pas a piloter le systeme. Ouvrir une
 * ecriture demanderait une authentification, une gestion de quotas et un modele
 * d autorisation qui n existent pas — et la table {@code clef_api} du socle reste
 * volontairement inexploitee.</p>
 *
 * <p><b>Aucune authentification, donc aucune donnee privee.</b> Les deux ressources
 * exposees sont deja publiques sur le site : le catalogue des prestations et
 * l identite commerciale du garage. Aucun membre, aucune commande, aucun rendez-vous
 * n est joignable par cette API, et les DTO lui sont propres pour qu un champ ajoute
 * a un ecran ne fuite pas ici par accident.</p>
 *
 * <p><b>DTO dedies, jamais d entite ni de vue Thymeleaf.</b> {@code ArticleVue} sert
 * les gabarits et peut changer avec un ecran ; le contrat d une API publique ne le
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
     * ferait charger tout le catalogue en memoire — l API est publique et anonyme,
     * elle doit borner ce qu un appelant peut reclamer.
     */
    private static final int TAILLE_MAX = 100;

    private final CatalogueService catalogue;
    private final IdentiteGarage identite;

    public ApiPubliqueController(CatalogueService catalogue, IdentiteGarage identite) {
        this.catalogue = catalogue;
        this.identite = identite;
    }

    @Operation(summary = "Liste les prestations proposees",
            description = "Prestations actives du garage, paginees. Aucune donnee de gestion "
                    + "(stock, marge, seuil) n est exposee.")
    @GetMapping("/prestations")
    public ResponseEntity<PagePublique<PrestationPublique>> prestations(
            @Parameter(description = "Index de page, a partir de zero")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Elements par page, 100 au maximum")
            @RequestParam(defaultValue = "" + TAILLE_DEFAUT) int taille) {

        List<PrestationPublique> toutes = catalogue.prestationsActives().stream()
                .map(PrestationPublique::de)
                .toList();
        return ResponseEntity.ok()
                // Catalogue public et peu changeant : une minute de cache suffit a
                // absorber un pic sans servir un tarif perime.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(paginer(toutes, page, taille));
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
     * liste vide — ce qui est la reponse juste, pas une erreur.</p>
     */
    private <T> PagePublique<T> paginer(List<T> tous, int page, int taille) {
        int tailleReelle = Math.clamp(taille, 1, TAILLE_MAX);
        int pageReelle = Math.max(page, 0);
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
