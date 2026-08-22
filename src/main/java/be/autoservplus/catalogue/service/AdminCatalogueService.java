package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Administration du catalogue depuis le back-office : creation et modification
 * des prestations (A1, A2) et des pieces (A4, A5), gestion du stock.
 *
 * <p>Meme decoupage que {@code AdminRdvService} : la protection d URL
 * {@code /admin/**} filtre le role ADMINISTRATEUR, et le service redouble par
 * {@code @PreAuthorize} de classe en defense en profondeur. La consultation
 * publique reste dans {@link CatalogueService}, qui n exige aucun role.</p>
 *
 * <p>Les modifications A2/A5 ne reecrivent jamais un document deja emis : les
 * lignes de panier, de commande, de RDV et d intervention recopient prix, libelle
 * et taux a leur creation (valeurs figees), le catalogue du jour n a pas voix au
 * chapitre sur l historique. Le service s appuie sur cette recopie, il n a donc
 * aucune precaution supplementaire a prendre ici — l invariant est prouve par
 * {@code AdminCatalogueServiceIT}.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminCatalogueService {

    private final CategorieRepository categories;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;

    public AdminCatalogueService(CategorieRepository categories,
                                 PrestationRepository prestations,
                                 PieceRepository pieces) {
        this.categories = categories;
        this.prestations = prestations;
        this.pieces = pieces;
    }

    // --- vues back-office --------------------------------------------------------------

    /** Catalogue complet des prestations, actives et inactives confondues. */
    public List<ArticleVueAdmin> prestationsPourAdmin() {
        return prestations.catalogueComplet().stream().map(ArticleVueAdmin::de).toList();
    }

    /** Catalogue complet des pieces, actives et inactives confondues. */
    public List<ArticleVueAdmin> piecesPourAdmin() {
        return pieces.catalogueComplet().stream().map(ArticleVueAdmin::de).toList();
    }

    public ArticleVueAdmin vuePrestation(UUID reference) {
        return ArticleVueAdmin.de(chargerPrestation(reference));
    }

    public ArticleVueAdmin vuePiece(UUID reference) {
        return ArticleVueAdmin.de(chargerPiece(reference));
    }

    public List<Categorie> categoriesDePrestations() {
        return categories.findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie.SERVICE);
    }

    public List<Categorie> categoriesDePieces() {
        return categories.findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie.PIECE);
    }

    // --- creation (A1, A4) -------------------------------------------------------------

    /**
     * Cree une categorie.
     *
     * @throws DoublonCatalogueException si le code est deja utilise
     */
    @Transactional
    public Categorie creerCategorie(String code, String libelle, TypeCategorie type) {
        // Unicite du code : invariant technique, aucun code RM du CdC ne la couvre.
        if (categories.existsByCode(code)) {
            throw new DoublonCatalogueException("code", code,
                    "Le code de categorie « %s » est deja utilise.".formatted(code));
        }
        return categories.save(new Categorie(code, libelle, type));
    }

    /**
     * Cree une prestation rattachee a une categorie de type SERVICE (A1).
     *
     * @throws DoublonCatalogueException si le code ou le nom sont deja utilises
     * @throws RegleMetierException      si la categorie est du mauvais type
     */
    @Transactional
    public Prestation creerPrestation(DonneesPrestation donnees) {
        // Unicite du code : invariant technique, sans code RM.
        if (prestations.existsByCode(donnees.code())) {
            throw new DoublonCatalogueException("code", donnees.code(),
                    "Le code de prestation « %s » est deja utilise.".formatted(donnees.code()));
        }
        // A1 : le nom du service est unique, validation serveur (le schema n impose
        // l unicite que sur le code — ajouter une contrainte en base demanderait une
        // migration, hors perimetre de ce lot).
        if (prestations.existsByLibelleIgnoreCase(donnees.libelle())) {
            throw new DoublonCatalogueException("libelle", donnees.libelle(),
                    "Une prestation nommee « %s » existe deja.".formatted(donnees.libelle()));
        }
        Categorie categorie = categorieDeType(donnees.codeCategorie(), TypeCategorie.SERVICE);
        Prestation prestation = new Prestation(categorie, donnees.code(), donnees.libelle(),
                donnees.prixHtva(), donnees.dureeMinutes());
        prestation.setDescription(donnees.description());
        prestation.setTauxTva(donnees.tauxTva());
        if (!donnees.actif()) {
            prestation.desactiver();
        }
        return prestations.save(prestation);
    }

    /**
     * Cree une piece rattachee a une categorie de type PIECE (A4), avec son stock initial.
     *
     * @throws DoublonCatalogueException si la reference fabricant existe deja
     * @throws RegleMetierException      si la categorie est du mauvais type
     */
    @Transactional
    public Piece creerPiece(DonneesPiece donnees) {
        // Unicite de la reference fabricant : invariant technique, sans code RM.
        if (pieces.existsByReferenceFabricant(donnees.referenceFabricant())) {
            throw new DoublonCatalogueException("referenceFabricant", donnees.referenceFabricant(),
                    "La reference fabricant « %s » est deja enregistree."
                            .formatted(donnees.referenceFabricant()));
        }
        Categorie categorie = categorieDeType(donnees.codeCategorie(), TypeCategorie.PIECE);
        Piece piece = new Piece(categorie, donnees.referenceFabricant(), donnees.libelle(),
                donnees.prixHtva());
        piece.setMarque(donnees.marque());
        piece.setDescription(donnees.description());
        piece.setTauxTva(donnees.tauxTva());
        piece.setQuantiteStock(donnees.quantiteStock());
        piece.setSeuilAlerte(donnees.seuilAlerte());
        if (!donnees.actif()) {
            piece.desactiver();
        }
        return pieces.save(piece);
    }

    // --- modification (A2, A5) ---------------------------------------------------------

    /**
     * Modifie une prestation (A2). Le code, identite technique, est immuable : le
     * champ {@code code} des donnees est ignore. Les documents deja emis ne sont pas
     * touches — leurs lignes portent des valeurs figees.
     *
     * @throws DoublonCatalogueException si le nouveau nom est pris par une autre prestation
     * @throws RegleMetierException      si la categorie visee est du mauvais type
     */
    @Transactional
    public Prestation modifierPrestation(UUID reference, DonneesPrestation donnees) {
        Prestation prestation = chargerPrestation(reference);
        // A1 s applique aussi a la modification : l exclusion de sa propre reference
        // permet de re-soumettre le formulaire sans changer le nom.
        if (prestations.existsByLibelleIgnoreCaseAndReferenceNot(donnees.libelle(), reference)) {
            throw new DoublonCatalogueException("libelle", donnees.libelle(),
                    "Une prestation nommee « %s » existe deja.".formatted(donnees.libelle()));
        }
        prestation.changerCategorie(categorieDeType(donnees.codeCategorie(), TypeCategorie.SERVICE));
        prestation.renommer(donnees.libelle());
        prestation.setDescription(donnees.description());
        prestation.modifierPrix(donnees.prixHtva());
        prestation.setTauxTva(donnees.tauxTva());
        prestation.setDureeMinutes(donnees.dureeMinutes());
        if (donnees.actif()) {
            prestation.activer();
        } else {
            prestation.desactiver();
        }
        return prestation;
    }

    /**
     * Modifie une piece (A5). La reference fabricant, ancre d unicite, est immuable :
     * le champ correspondant des donnees est ignore.
     *
     * @throws RegleMetierException si la categorie visee est du mauvais type
     */
    @Transactional
    public Piece modifierPiece(UUID reference, DonneesPiece donnees) {
        Piece piece = chargerPiece(reference);
        piece.changerCategorie(categorieDeType(donnees.codeCategorie(), TypeCategorie.PIECE));
        piece.renommer(donnees.libelle());
        piece.setMarque(donnees.marque());
        piece.setDescription(donnees.description());
        piece.modifierPrix(donnees.prixHtva());
        piece.setTauxTva(donnees.tauxTva());
        piece.setQuantiteStock(donnees.quantiteStock());
        piece.setSeuilAlerte(donnees.seuilAlerte());
        if (donnees.actif()) {
            piece.activer();
        } else {
            piece.desactiver();
        }
        return piece;
    }

    // --- stock -------------------------------------------------------------------------

    /** Pieces dont le stock a atteint le seuil d alerte, pour le tableau de bord du gerant. */
    public List<Piece> piecesEnAlerteDeStock() {
        return pieces.enAlerteDeStock();
    }

    @Transactional
    public void reapprovisionner(UUID reference, int quantite) {
        chargerPiece(reference).ajouterAuStock(quantite);
    }

    // --- helpers -----------------------------------------------------------------------

    private Prestation chargerPrestation(UUID reference) {
        return prestations.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
    }

    private Piece chargerPiece(UUID reference) {
        return pieces.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", reference));
    }

    /**
     * Charge une categorie et verifie sa coherence de type : une prestation vit dans
     * une categorie SERVICE, une piece dans une categorie PIECE. L entite redouble
     * cette garde dans {@code changerCategorie} — ici, le refus produit un message
     * metier uniforme pour la creation comme pour la modification.
     */
    private Categorie categorieDeType(String code, TypeCategorie typeAttendu) {
        Categorie categorie = categories.findByCode(code)
                .orElseThrow(() -> new RessourceIntrouvableException("Categorie", code));
        if (categorie.getType() != typeAttendu) {
            throw new RegleMetierException(typeAttendu == TypeCategorie.SERVICE
                    ? "La categorie « %s » est destinee aux pieces, pas aux prestations.".formatted(code)
                    : "La categorie « %s » est destinee aux prestations, pas aux pieces.".formatted(code));
        }
        return categorie;
    }
}
