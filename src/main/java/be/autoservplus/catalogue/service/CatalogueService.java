package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Consultation et administration du catalogue.
 *
 * <p>Couvre les fonctionnalites F3, F4 et F5 du cote public, ainsi que A1 a A6 du cote
 * back-office. Seuls les elements actifs sont exposes au visiteur : desactiver une
 * prestation la retire des surfaces publiques sans effacer son historique de vente.</p>
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

    public List<Prestation> prestationsActives() {
        return prestations.findByActifTrueOrderByLibelleAsc();
    }

    public List<Prestation> prestationsDeCategorie(String codeCategorie) {
        return prestations.findByCategorieCodeAndActifTrueOrderByLibelleAsc(codeCategorie);
    }

    public List<Piece> piecesActives() {
        return pieces.findByActifTrueOrderByLibelleAsc();
    }

    public List<Piece> piecesDeCategorie(String codeCategorie) {
        return pieces.findByCategorieCodeAndActifTrueOrderByLibelleAsc(codeCategorie);
    }

    public Prestation prestationParReference(UUID reference) {
        return prestations.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
    }

    public Piece pieceParReference(UUID reference) {
        return pieces.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", reference));
    }

    /**
     * Recherche dans les prestations.
     *
     * <p>Un terme trop court renvoie une page vide plutot que l integralite du catalogue :
     * une requete sur un seul caractere ramenerait presque tout et n aiderait personne.</p>
     */
    public Page<Prestation> rechercherPrestations(String terme, int numeroPage) {
        if (termeInsuffisant(terme)) {
            return Page.empty();
        }
        return prestations.rechercher(terme.trim(), pagination(numeroPage));
    }

    public Page<Piece> rechercherPieces(String terme, int numeroPage) {
        if (termeInsuffisant(terme)) {
            return Page.empty();
        }
        return pieces.rechercher(terme.trim(), pagination(numeroPage));
    }

    // --- administration ---------------------------------------------------------------

    /**
     * Cree une categorie.
     *
     * @throws RegleMetierException si le code est deja utilise
     */
    @Transactional
    public Categorie creerCategorie(String code, String libelle, TypeCategorie type) {
        if (categories.existsByCode(code)) {
            throw new RegleMetierException("RM-28",
                    "Le code de categorie « %s » est deja utilise.".formatted(code));
        }
        return categories.save(new Categorie(code, libelle, type));
    }

    /**
     * Cree une prestation rattachee a une categorie de type SERVICE.
     *
     * @throws RegleMetierException si le code existe deja ou si la categorie est du mauvais type
     */
    @Transactional
    public Prestation creerPrestation(String codeCategorie, String code, String libelle,
                                      BigDecimal prixHtva, int dureeMinutes) {
        if (prestations.existsByCode(code)) {
            throw new RegleMetierException("RM-29",
                    "Le code de prestation « %s » est deja utilise.".formatted(code));
        }
        Categorie categorie = categorieParCode(codeCategorie);
        if (categorie.getType() != TypeCategorie.SERVICE) {
            throw new RegleMetierException("RM-29",
                    "La categorie « %s » est destinee aux pieces, pas aux prestations."
                            .formatted(codeCategorie));
        }
        return prestations.save(new Prestation(categorie, code, libelle, prixHtva, dureeMinutes));
    }

    /**
     * Cree une piece rattachee a une categorie de type PIECE.
     *
     * @throws RegleMetierException si la reference fabricant existe deja ou si la
     *                              categorie est du mauvais type
     */
    @Transactional
    public Piece creerPiece(String codeCategorie, String referenceFabricant,
                            String libelle, BigDecimal prixHtva) {
        if (pieces.existsByReferenceFabricant(referenceFabricant)) {
            throw new RegleMetierException("RM-29",
                    "La reference fabricant « %s » est deja enregistree.".formatted(referenceFabricant));
        }
        Categorie categorie = categorieParCode(codeCategorie);
        if (categorie.getType() != TypeCategorie.PIECE) {
            throw new RegleMetierException("RM-29",
                    "La categorie « %s » est destinee aux prestations, pas aux pieces."
                            .formatted(codeCategorie));
        }
        return pieces.save(new Piece(categorie, referenceFabricant, libelle, prixHtva));
    }

    /** Pieces dont le stock a atteint le seuil d alerte, pour le tableau de bord du gerant. */
    public List<Piece> piecesEnAlerteDeStock() {
        return pieces.enAlerteDeStock();
    }

    @Transactional
    public void reapprovisionner(UUID reference, int quantite) {
        pieceParReference(reference).ajouterAuStock(quantite);
    }

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