package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.HistoriqueModificationCatalogue;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.TypeEntiteCatalogue;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.HistoriqueModificationCatalogueRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.catalogue.service.dto.ModificationCatalogueVue;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.AuteurCourant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>A2/A5 exigent aussi que les modifications soient <b>historisees (qui, quand,
 * quoi)</b> : chaque champ metier reellement modifie ecrit une ligne de
 * {@code historique_modification_catalogue}, dans la meme transaction que la
 * modification. Ce journal trace l evolution du <b>catalogue</b> et rien d autre —
 * il ne dit rien des documents deja emis, qui par construction n ont pas bouge.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminCatalogueService {

    private final CategorieRepository categories;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;
    private final HistoriqueModificationCatalogueRepository historiques;
    private final AuteurCourant auteurCourant;
    private final Clock horloge;

    public AdminCatalogueService(CategorieRepository categories,
                                 PrestationRepository prestations,
                                 PieceRepository pieces,
                                 HistoriqueModificationCatalogueRepository historiques,
                                 AuteurCourant auteurCourant,
                                 Clock horloge) {
        this.categories = categories;
        this.prestations = prestations;
        this.pieces = pieces;
        this.historiques = historiques;
        this.auteurCourant = auteurCourant;
        this.horloge = horloge;
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
        // Photo AVANT prise apres les refus : une modification rejetee n a rien change,
        // elle n a donc rien a historiser.
        Map<String, String> avant = etatDe(prestation);
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
        historiser(TypeEntiteCatalogue.PRESTATION, prestation.getId(), avant, etatDe(prestation));
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
        Map<String, String> avant = etatDe(piece);
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
        historiser(TypeEntiteCatalogue.PIECE, piece.getId(), avant, etatDe(piece));
        return piece;
    }

    // --- historique des modifications (A2, A5) -----------------------------------------

    /** Historique des modifications d une prestation, du plus recent au plus ancien (A2). */
    public List<ModificationCatalogueVue> historiquePrestation(UUID reference) {
        return historique(TypeEntiteCatalogue.PRESTATION, chargerPrestation(reference).getId());
    }

    /** Historique des modifications d une piece, du plus recent au plus ancien (A5). */
    public List<ModificationCatalogueVue> historiquePiece(UUID reference) {
        return historique(TypeEntiteCatalogue.PIECE, chargerPiece(reference).getId());
    }

    private List<ModificationCatalogueVue> historique(TypeEntiteCatalogue type, Long entiteId) {
        return historiques.historiqueDe(type, entiteId).stream()
                .map(ModificationCatalogueVue::de)
                .toList();
    }

    // --- suppression ou desactivation (A3, A6) -----------------------------------------

    /** Diagnostic RM-29 pour l ecran de confirmation : l action adequate est proposee. */
    public PropositionSuppression propositionSuppressionPrestation(UUID reference) {
        Prestation prestation = chargerPrestation(reference);
        return new PropositionSuppression(prestation.getReference(), prestation.getCode(),
                prestation.getLibelle(), prestations.nombreReferencesHistoriques(prestation.getId()),
                prestation.isActif());
    }

    /** Diagnostic RM-29 pour l ecran de confirmation : l action adequate est proposee. */
    public PropositionSuppression propositionSuppressionPiece(UUID reference) {
        Piece piece = chargerPiece(reference);
        return new PropositionSuppression(piece.getReference(), piece.getReferenceFabricant(),
                piece.getLibelle(), pieces.nombreReferencesHistoriques(piece.getId()),
                piece.isActif());
    }

    /**
     * Supprime definitivement une prestation (A3), sous la garde <b>RM-29</b> : refuse
     * des qu un historique (reservation, panier, commande, intervention) la reference —
     * seule la desactivation est alors permise.
     *
     * <p>Divergence assumee avec le principe « aucune suppression physique » du socle
     * ({@code BaseEntity}) : pour un element jamais reference, la suppression definitive
     * du CdC est prise au pied de la lettre. Il n y a aucun historique a proteger, et un
     * simple soft delete gelerait a jamais son code ou sa reference fabricant, les
     * contraintes d unicite n etant pas partielles sur {@code deleted_at}.</p>
     *
     * @throws SuppressionRefuseeException si au moins une reference existe
     */
    @Transactional
    public void supprimerDefinitivementPrestation(UUID reference) {
        Prestation prestation = chargerPrestation(reference);
        long referencesHistoriques = prestations.nombreReferencesHistoriques(prestation.getId());
        if (referencesHistoriques > 0) {
            throw new SuppressionRefuseeException(prestation.getLibelle(), referencesHistoriques);
        }
        supprimerEnBase(prestation.getLibelle(), () -> {
            prestations.delete(prestation);
            prestations.flush();
        });
    }

    /**
     * Supprime definitivement une piece (A6), sous la meme garde <b>RM-29</b> que la
     * prestation.
     *
     * @throws SuppressionRefuseeException si au moins une reference existe
     */
    @Transactional
    public void supprimerDefinitivementPiece(UUID reference) {
        Piece piece = chargerPiece(reference);
        long referencesHistoriques = pieces.nombreReferencesHistoriques(piece.getId());
        if (referencesHistoriques > 0) {
            throw new SuppressionRefuseeException(piece.getLibelle(), referencesHistoriques);
        }
        supprimerEnBase(piece.getLibelle(), () -> {
            pieces.delete(piece);
            pieces.flush();
        });
    }

    /** Desactivation RM-28 : disparait du catalogue public, reste lisible des historiques. */
    @Transactional
    public void desactiverPrestation(UUID reference) {
        chargerPrestation(reference).desactiver();
    }

    @Transactional
    public void activerPrestation(UUID reference) {
        chargerPrestation(reference).activer();
    }

    /** Desactivation RM-28 : disparait du catalogue public, reste lisible des historiques. */
    @Transactional
    public void desactiverPiece(UUID reference) {
        chargerPiece(reference).desactiver();
    }

    @Transactional
    public void activerPiece(UUID reference) {
        chargerPiece(reference).activer();
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

    /**
     * Ecrit l historique d une modification (A2, A5) : <b>une ligne par champ
     * reellement modifie</b>, dans la meme transaction que la modification elle-meme.
     * Un echec ulterieur annule le changement et sa trace ensemble — le journal ne
     * peut ni manquer une modification ni en inventer une.
     *
     * <p>Le diff se fait sur deux photos <b>textuelles</b> de l entite plutot que
     * champ a champ en Java. Deux consequences voulues : la normalisation (echelle des
     * montants notamment) a lieu une seule fois, si bien qu un prix re-soumis
     * « 75.0 » contre « 75.00 » stocke ne produit aucune ligne fantome ; et la valeur
     * comparee est exactement celle qui sera stockee, sans traduction intermediaire.</p>
     *
     * <p>Une seule lecture de l horloge et une seule resolution de l auteur pour tout
     * le lot : les lignes d une meme modification partagent leur horodatage, ce qui les
     * rend reconnaissables comme un seul geste d administration.</p>
     */
    private void historiser(TypeEntiteCatalogue type, Long entiteId,
                            Map<String, String> avant, Map<String, String> apres) {
        Instant maintenant = horloge.instant();
        Utilisateur auteur = auteurCourant.resoudre();
        avant.forEach((champ, valeurAvant) -> {
            String valeurApres = apres.get(champ);
            if (!Objects.equals(valeurAvant, valeurApres)) {
                historiques.save(new HistoriqueModificationCatalogue(
                        type, entiteId, champ, valeurAvant, valeurApres, maintenant, auteur));
            }
        });
    }

    /**
     * Photo des champs <b>metier</b> d une prestation, dans l ordre du formulaire.
     * Les colonnes d audit ({@code created_*}, {@code updated_*}) en sont exclues :
     * elles decrivent l ecriture, pas la decision de gestion — les historiser
     * reviendrait a journaliser le journal. Le code n y figure pas non plus : identite
     * technique immuable, la modification ne peut pas le changer.
     *
     * <p>{@code LinkedHashMap} et non {@code Map.of} : l ordre d insertion determine
     * l ordre des lignes ecrites pour une meme modification, et les valeurs sont
     * nullables (une description peut ne pas exister).</p>
     */
    private static Map<String, String> etatDe(Prestation prestation) {
        Map<String, String> etat = new LinkedHashMap<>();
        etat.put("categorie", prestation.getCategorie().getCode());
        etat.put("libelle", prestation.getLibelle());
        etat.put("description", prestation.getDescription());
        etat.put("prixHtva", montant(prestation.getPrixHtva()));
        etat.put("tauxTva", montant(prestation.getTauxTva()));
        etat.put("dureeMinutes", String.valueOf(prestation.getDureeMinutes()));
        etat.put("actif", String.valueOf(prestation.isActif()));
        return etat;
    }

    /** Photo des champs metier d une piece ; la reference fabricant, immuable, en est exclue. */
    private static Map<String, String> etatDe(Piece piece) {
        Map<String, String> etat = new LinkedHashMap<>();
        etat.put("categorie", piece.getCategorie().getCode());
        etat.put("libelle", piece.getLibelle());
        etat.put("marque", piece.getMarque());
        etat.put("description", piece.getDescription());
        etat.put("prixHtva", montant(piece.getPrixHtva()));
        etat.put("tauxTva", montant(piece.getTauxTva()));
        etat.put("quantiteStock", String.valueOf(piece.getQuantiteStock()));
        etat.put("seuilAlerte", String.valueOf(piece.getSeuilAlerte()));
        etat.put("actif", String.valueOf(piece.isActif()));
        return etat;
    }

    /**
     * Forme canonique d un montant ou d un taux : deux decimales, comme les colonnes
     * {@code numeric(_, 2)} du schema. Sans cette normalisation, la comparaison
     * textuelle prendrait « 21 » et « 21.00 » pour deux valeurs differentes et
     * inventerait une modification a chaque re-soumission du formulaire.
     */
    private static String montant(BigDecimal valeur) {
        return valeur == null ? null : valeur.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Execute le DELETE avec flush immediat : si une reference est apparue entre le
     * comptage et la suppression, la FK {@code ON DELETE RESTRICT} refuse — seconde
     * ligne de defense de RM-29, la course est traduite en refus metier plutot qu en
     * erreur 500 au commit.
     */
    private void supprimerEnBase(String libelle, Runnable suppression) {
        try {
            suppression.run();
        } catch (DataIntegrityViolationException e) {
            throw new SuppressionRefuseeException(libelle, 1);
        }
    }

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
