package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    private static final int LONGUEUR_MINIMALE_RECHERCHE = 2;

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

    public List<Categorie> categoriesDePrestations() {
        return categories.findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie.SERVICE);
    }

    public List<Categorie> categoriesDePieces() {
        return categories.findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie.PIECE);
    }

    public List<ArticleVue> prestationsActives() {
        return prestations.findByActifTrueOrderByLibelleAsc().stream().map(ArticleVue::de).toList();
    }

    public List<ArticleVue> prestationsDeCategorie(String codeCategorie) {
        return prestations.findByCategorieCodeAndActifTrueOrderByLibelleAsc(codeCategorie)
                .stream().map(ArticleVue::de).toList();
    }

    public List<ArticleVue> piecesActives() {
        return pieces.findByActifTrueOrderByLibelleAsc().stream().map(ArticleVue::de).toList();
    }

    public List<ArticleVue> piecesDeCategorie(String codeCategorie) {
        return pieces.findByCategorieCodeAndActifTrueOrderByLibelleAsc(codeCategorie)
                .stream().map(ArticleVue::de).toList();
    }

    public ArticleVue vuePrestation(UUID reference) {
        return ArticleVue.de(prestationParReference(reference));
    }

    public ArticleVue vuePiece(UUID reference) {
        return ArticleVue.de(pieceParReference(reference));
    }

    /**
     * Recherche dans les prestations.
     *
     * <p>Un terme trop court renvoie une page vide plutot que l integralite du catalogue :
     * une requete sur un seul caractere ramenerait presque tout et n aiderait personne.</p>
     */
    public Page<ArticleVue> rechercherPrestations(String terme, int numeroPage) {
        if (termeInsuffisant(terme)) {
            return Page.empty();
        }
        return prestations.rechercher(terme.trim(), pagination(numeroPage)).map(ArticleVue::de);
    }

    public Page<ArticleVue> rechercherPieces(String terme, int numeroPage) {
        if (termeInsuffisant(terme)) {
            return Page.empty();
        }
        return pieces.rechercher(terme.trim(), pagination(numeroPage)).map(ArticleVue::de);
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

    // --- administration ---------------------------------------------------------------
    // La creation et la modification (A1, A2, A4, A5) vivent dans AdminCatalogueService,
    // protege par @PreAuthorize. Ne restent ici que les suppressions logiques, en
    // attendant leur remplacement par la suppression conditionnelle RM-29.

    /** Retire une prestation du catalogue par suppression logique. */
    @Transactional
    public void supprimerPrestation(UUID reference, String auteur) {
        prestationParReference(reference).marquerSupprime(auteur);
    }

    @Transactional
    public void supprimerPiece(UUID reference, String auteur) {
        pieceParReference(reference).marquerSupprime(auteur);
    }

    // --- utilitaires -------------------------------------------------------------------

    private Categorie categorieParCode(String code) {
        return categories.findByCode(code)
                .orElseThrow(() -> new RessourceIntrouvableException("Categorie", code));
    }

    private boolean termeInsuffisant(String terme) {
        return terme == null || terme.trim().length() < LONGUEUR_MINIMALE_RECHERCHE;
    }

    private Pageable pagination(int numeroPage) {
        return PageRequest.of(Math.max(0, numeroPage), TAILLE_PAGE_PAR_DEFAUT);
    }
}