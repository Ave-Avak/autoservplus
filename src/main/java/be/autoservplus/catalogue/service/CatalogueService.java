package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.Rayon;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.catalogue.service.dto.CategorieVue;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.web.NumeroPage;
import be.autoservplus.i18n.LanguesSupportees;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Consultation publique du catalogue.
 *
 * <p>Couvre les fonctionnalites F3, F4 et F5. Seuls les elements actifs sont exposes
 * au visiteur : desactiver une prestation la retire des surfaces publiques sans
 * effacer son historique de vente. Le back-office (A1 a A6) vit dans
 * {@link AdminCatalogueService}, protege par role.</p>
 */
@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private static final int TAILLE_PAGE_PAR_DEFAUT = 12;
    public static final int LONGUEUR_MINIMALE_RECHERCHE = 2;
    /** Longueur maximale d'un terme de recherche, celle des deux champs de saisie. */
    public static final int LONGUEUR_MAXIMALE_RECHERCHE = 100;

    private final CategorieRepository categories;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;

    public CatalogueService(CategorieRepository categories,
                            PrestationRepository prestations,
                            PieceRepository pieces) {
        this.categories = categories;
        this.prestations = prestations;
        this.pieces = pieces;
    }

    // --- consultation publique --------------------------------------------------------

    // Les textes sont rendus dans la langue de la requête en cours, le français hors de
    // toute requête. Une traduction absente retombe sur le français, et la vue le dit.

    public List<CategorieVue> categoriesDePrestations() {
        Locale langue = langue();
        return categories.findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie.SERVICE).stream()
                .map(categorie -> CategorieVue.de(categorie, langue)).toList();
    }

    public List<ArticleVue> prestationsActives() {
        Locale langue = langue();
        return parCategorie(prestations.findByActifTrueOrderByLibelleAsc().stream(), Prestation::getCategorie,
                prestation -> ArticleVue.de(prestation, langue), langue);
    }

    /** Les prestations actives dans une langue donnée, pour un appelant qui ne suit pas celle de l'écran. */
    public List<ArticleVue> prestationsActives(Locale langue) {
        return triees(prestations.findByActifTrueOrderByLibelleAsc().stream()
                .map(prestation -> ArticleVue.de(prestation, langue)), langue);
    }

    /** Prestations que le garage montre à l'accueil, dans son ordre (V80). */
    public List<ArticleVue> prestationsDeLaVitrine() {
        Locale langue = langue();
        return prestations.findByActifTrueAndOrdreVitrineNotNullOrderByOrdreVitrineAsc().stream()
                .map(prestation -> ArticleVue.de(prestation, langue))
                .toList();
    }

    public List<ArticleVue> prestationsDeCategorie(String codeCategorie) {
        Locale langue = langue();
        return triees(prestations.findByCategorieCodeAndActifTrueOrderByLibelleAsc(codeCategorie).stream()
                .map(prestation -> ArticleVue.de(prestation, langue)), langue);
    }

    public List<ArticleVue> piecesActives() {
        Locale langue = langue();
        return triees(pieces.findByActifTrueOrderByLibelleAsc().stream()
                .map(piece -> ArticleVue.de(piece, langue)), langue);
    }

    /** Produits actifs dans une langue donnée, d'un rayon ou des trois, pour l'API publique. */
    public List<ArticleVue> produitsActifs(Locale langue, Rayon rayonOuTous) {
        List<Piece> trouves = rayonOuTous == null
                ? pieces.findByActifTrueOrderByLibelleAsc()
                : pieces.actifsDuRayon(rayonOuTous);
        return triees(trouves.stream().map(piece -> ArticleVue.de(piece, langue)), langue);
    }

    /** Catégories actives d'un rayon, pour le filtre de sa liste. */
    public List<CategorieVue> categoriesDuRayon(Rayon rayon) {
        Locale langue = langue();
        return categories.findByRayonAndActifTrueOrderByOrdreAsc(rayon).stream()
                .map(categorie -> CategorieVue.de(categorie, langue)).toList();
    }

    /**
     * Produits actifs d'un rayon, d'une seule de ses catégories quand un code est donné. Un
     * code qui appartient à un autre rayon ne rend rien : le filtre d'une liste ne montre
     * jamais les articles d'une autre.
     */
    public List<ArticleVue> produitsDuRayon(Rayon rayon, String codeCategorie) {
        Locale langue = langue();
        List<Piece> trouves = (codeCategorie == null || codeCategorie.isBlank())
                ? pieces.actifsDuRayon(rayon)
                : pieces.actifsDuRayonEtDeCategorie(rayon, codeCategorie);
        return parCategorie(trouves.stream(), Piece::getCategorie, piece -> ArticleVue.de(piece, langue), langue);
    }

    public ArticleVue vuePrestation(UUID reference) {
        return ArticleVue.de(prestationParReference(reference), langue());
    }

    public ArticleVue vuePiece(UUID reference) {
        return ArticleVue.de(pieceParReference(reference), langue());
    }

    /**
     * Recherche dans les prestations.
     *
     * <p>Un terme trop court renvoie une page vide plutôt que l'intégralité du catalogue :
     * une requête sur un seul caractère ramènerait presque tout et n'aiderait personne.</p>
     *
     * <p>Une page demandée au-delà de la dernière rend la dernière : une adresse gardée en
     * favori reste utile quand le catalogue a rétréci.</p>
     */
    public Page<ArticleVue> rechercherPrestations(String terme, int numeroPage) {
        if (termeTropCourt(terme)) {
            return Page.empty();
        }
        List<String> motifs = MotifRecherche.mots(terme);
        Locale langue = langue();
        Page<Prestation> page = prestations.rechercher(motifs, langue.getLanguage(), pagination(numeroPage));
        if (page.isEmpty() && page.getTotalElements() > 0) {
            page = prestations.rechercher(motifs, langue.getLanguage(), pagination(page.getTotalPages() - 1));
        }
        return page.map(prestation -> ArticleVue.de(prestation, langue));
    }

    /** Recherche dans les produits d'un rayon, aux mêmes règles que {@link #rechercherPrestations}. */
    public Page<ArticleVue> rechercherProduits(String terme, Rayon rayon, int numeroPage) {
        if (termeTropCourt(terme)) {
            return Page.empty();
        }
        List<String> motifs = MotifRecherche.mots(terme);
        Locale langue = langue();
        Page<Piece> page = pieces.rechercher(motifs, rayon, langue.getLanguage(), pagination(numeroPage));
        if (page.isEmpty() && page.getTotalElements() > 0) {
            page = pieces.rechercher(motifs, rayon, langue.getLanguage(), pagination(page.getTotalPages() - 1));
        }
        return page.map(piece -> ArticleVue.de(piece, langue));
    }

    /**
     * Terme nettoyé et coupé à {@link #LONGUEUR_MAXIMALE_RECHERCHE} caractères. Le champ
     * porte la même limite, mais une adresse écrite à la main ne passe pas par lui. Le
     * terme est coupé plutôt que refusé : la recherche répond sur ce qu'elle a gardé, et
     * le champ le montre.
     */
    public static String termeBorne(String saisie) {
        String terme = saisie.trim();
        if (terme.codePointCount(0, terme.length()) <= LONGUEUR_MAXIMALE_RECHERCHE) {
            return terme;
        }
        return terme.substring(0, terme.offsetByCodePoints(0, LONGUEUR_MAXIMALE_RECHERCHE)).trim();
    }

    /** Vrai pour un terme absent ou plus court que {@link #LONGUEUR_MINIMALE_RECHERCHE}. */
    public boolean termeTropCourt(String terme) {
        return terme == null || terme.trim().length() < LONGUEUR_MINIMALE_RECHERCHE;
    }

    /** Entite complete, reservee aux operations d administration. */
    public Prestation prestationParReference(UUID reference) {
        return prestations.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
    }

    /** Entite complete, reservee aux operations d administration. */
    public Piece pieceParReference(UUID reference) {
        return pieces.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", reference));
    }

    // --- utilitaires -------------------------------------------------------------------

    /** Langue de la requête en cours, ramenée à l'une des trois du site. */
    private static Locale langue() {
        return LanguesSupportees.plusProche(LocaleContextHolder.getLocale());
    }

    /**
     * Les articles dans l'ordre alphabétique de la langue affichée. La base les rend
     * triés sur le libellé français : une liste néerlandaise serait dans le désordre.
     * Le catalogue d'un garage tient en mémoire, le tri s'y fait donc ici.
     */
    private static List<ArticleVue> triees(Stream<ArticleVue> articles, Locale langue) {
        Collator ordre = Collator.getInstance(langue);
        return articles.sorted(Comparator.comparing(ArticleVue::libelle, ordre)).toList();
    }

    /**
     * Les articles des listes publiques, rangés comme les catégories du filtre, puis dans
     * l'ordre alphabétique de la langue affichée. L'ordre alphabétique seul ouvrait la liste
     * des prestations sur le changement saisonnier des roues et celle des pièces sur les
     * amortisseurs, loin de l'entretien courant et des filtres.
     */
    private static <T> List<ArticleVue> parCategorie(Stream<T> elements, Function<T, Categorie> categorie,
                                                     Function<T, ArticleVue> vue, Locale langue) {
        Collator ordre = Collator.getInstance(langue);
        record Rangee(short rang, ArticleVue vue) {
        }
        return elements.map(element -> new Rangee(categorie.apply(element).getOrdre(), vue.apply(element)))
                .sorted(Comparator.comparingInt(Rangee::rang).thenComparing(rangee -> rangee.vue().libelle(), ordre))
                .map(Rangee::vue)
                .toList();
    }

    private Pageable pagination(int numeroPage) {
        return PageRequest.of(NumeroPage.borne(numeroPage, TAILLE_PAGE_PAR_DEFAUT), TAILLE_PAGE_PAR_DEFAUT);
    }
}