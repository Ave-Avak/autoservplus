package be.autoservplus.intervention.domain;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.ConvertisseurChoixPieces;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Article facturable d une intervention : main d oeuvre (prestation du catalogue)
 * ou piece detachee. Le libelle et les prix sont figes a la creation, pour que
 * l historique reste stable meme si le catalogue evolue : invariant de conception
 * sans numero de regle au CdC, meme recopie que
 * {@link be.autoservplus.reservation.domain.LigneRdv}.
 *
 * <p>Pas de suppression logique : la ligne n existe que par son intervention,
 * un retrait passe par {@code orphanRemoval} de la relation parente.</p>
 *
 * <p><b>Marqueurs RM-15</b> : l etat d une ligne se lit sur le <b>couple</b>
 * {@code (ajouteeEnCours, accordMembre)}, aligne sur le dictionnaire de donnees
 * (Livrable 09) :</p>
 * <table>
 *   <caption>Encodage des quatre etats</caption>
 *   <tr><td>{@code (false, null)}</td><td>ligne du RDV : le devis initial, hors RM-15</td></tr>
 *   <tr><td>{@code (true,  null)}</td><td>ajout du garage, en attente de reponse</td></tr>
 *   <tr><td>{@code (true,  true)}</td><td>ajout accepte : facturable</td></tr>
 *   <tr><td>{@code (true, false)}</td><td>ajout refuse : conserve, hors total, non execute</td></tr>
 * </table>
 *
 * <p>Un champ a trois valeurs plutot que deux booleens : l etat absurde
 * « acceptee ET refusee » devient <b>inexprimable</b> au lieu d etre interdit par
 * un CHECK. Le seul CHECK restant, {@code ck_ligne_interv_accord}, verrouille la
 * premiere ligne du tableau : une ligne du devis initial ne porte aucun accord.</p>
 */
@Entity
@Table(name = "ligne_intervention")
@EntityListeners(AuditingEntityListener.class)
public class LigneIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private Intervention intervention;

    // Exactement un des deux est non-null (CHECK ck_ligne_interv_article).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Prestation prestation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id")
    private Piece piece;

    @NotNull
    @Column(name = "libelle_fige", nullable = false, length = 150)
    private String libelleFige;

    /** Entière pour une pièce ou un forfait, en heures au centième pour une ligne à l'heure (V73). */
    @Column(name = "quantite", nullable = false, precision = 7, scale = 2)
    private BigDecimal quantite;

    /** Figée à la création, comme le libellé et le prix (V73). */
    @Enumerated(EnumType.STRING)
    @Column(name = "unite", nullable = false, length = 10, updatable = false)
    private UniteLigne unite;

    @Column(name = "prix_unitaire_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaireHtva;

    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

    /**
     * Repris de la ligne d'origine, ou fixé à l'ajout par le garage (V77). Nul hors forfait
     * ({@code ck_ligne_interv_pieces_client}) et pour un forfait sans prix pose seule.
     */
    @Convert(converter = ConvertisseurChoixPieces.class)
    @Column(name = "pieces_client", updatable = false)
    private ChoixPieces choixPieces;

    @Column(name = "ajoutee_en_cours", nullable = false)
    private boolean ajouteeEnCours;

    /**
     * Reponse du membre sur une ligne ajoutee en cours (RM-15). {@code null} vaut
     * « pas de reponse » : soit la ligne n en attend aucune (devis initial), soit
     * elle attend celle du membre. C est le couple avec {@link #ajouteeEnCours} qui
     * tranche : voir les predicats derives plus bas.
     *
     * <p>{@link Boolean} et non {@code boolean} : le troisieme etat est porteur de
     * sens metier, pas un trou de donnee. Aucun DEFAULT en base pour la meme raison.</p>
     */
    @Column(name = "accord_membre")
    private Boolean accordMembre;

    /**
     * Pièces comprises dans le prix d'un forfait dont le garage fournit les pièces (V77). Vide
     * pour toute autre ligne.
     */
    @OneToMany(mappedBy = "ligne", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PieceIncluse> piecesIncluses = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 120, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected LigneIntervention() {
        // requis par JPA
    }

    /**
     * Ligne de main d oeuvre creee depuis une prestation du catalogue.
     *
     * @param ajouteeEnCours {@code false} pour une ligne issue du RDV (devis initial),
     *                       {@code true} pour un ajout du garage pendant l intervention.
     * @param choixPieces    choix figé avec le prix, {@code null} quand aucun choix n'a été
     *                       proposé. Le prix est déjà celui de ce choix : il n'est pas relu
     *                       au catalogue.
     * @throws QuantiteLigneRefuseeException quantité nulle, négative, décimale pour un
     *                                       forfait ou trop précise pour des heures
     */
    LigneIntervention(Intervention intervention, Prestation prestation, BigDecimal quantite,
                      BigDecimal prixUnitaireHtva, BigDecimal tauxTva, boolean ajouteeEnCours,
                      ChoixPieces choixPieces) {
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        this.prestation = Objects.requireNonNull(prestation, "prestation");
        this.unite = UniteLigne.de(prestation);
        if (choixPieces != null && unite != UniteLigne.FORFAIT) {
            throw new IllegalArgumentException("Seul un forfait porte un choix des pièces.");
        }
        this.choixPieces = choixPieces;
        this.quantite = quantiteValide(quantite, unite);
        this.libelleFige = prestation.libelle(intervention.langueDesLibelles()).texte();
        this.prixUnitaireHtva = Objects.requireNonNull(prixUnitaireHtva, "prixUnitaireHtva");
        this.tauxTva = Objects.requireNonNull(tauxTva, "tauxTva");
        this.ajouteeEnCours = ajouteeEnCours;
        copierLaComposition(prestation);
    }

    /**
     * Les pièces que la prestation consomme deviennent les pièces incluses de la ligne, fois sa
     * quantité : deux vidanges consomment deux filtres. Copiées ici plutôt que relues au
     * catalogue à la clôture : le garage les ajuste au travail réellement fait, et une
     * composition changée après coup ne touche pas un dossier ouvert.
     */
    private void copierLaComposition(Prestation prestation) {
        if (!porteDesPiecesIncluses()) {
            return;
        }
        int fois = quantite.intValueExact();
        prestation.getComposition().forEach(composant -> piecesIncluses.add(
                new PieceIncluse(this, composant.getPiece(), composant.getQuantite() * fois)));
    }

    /**
     * Un forfait dont le garage fournit les pièces, choix proposé ou non. Une prestation à
     * l'heure se facture au temps passé, une pose seule consomme les pièces du client.
     */
    public boolean porteDesPiecesIncluses() {
        return unite == UniteLigne.FORFAIT && choixPieces != ChoixPieces.APPORTEES_PAR_CLIENT;
    }

    /**
     * Ajoute une pièce incluse, ou change sa quantité si la ligne la porte déjà.
     *
     * @throws PieceIncluseRefuseeException ligne sans pièces incluses, ou quantité inférieure à un
     */
    ChangementPieceIncluse fixerPieceIncluse(Piece pieceIncluse, int nouvelleQuantite) {
        Objects.requireNonNull(pieceIncluse, "pieceIncluse");
        if (!porteDesPiecesIncluses()) {
            throw PieceIncluseRefuseeException.horsForfaitFourni();
        }
        if (nouvelleQuantite < 1) {
            throw PieceIncluseRefuseeException.quantite();
        }
        for (PieceIncluse incluse : piecesIncluses) {
            if (incluse.porte(pieceIncluse)) {
                int avant = incluse.getQuantite();
                incluse.changerQuantite(nouvelleQuantite);
                return new ChangementPieceIncluse(this, pieceIncluse, EvenementPieceIncluse.MODIFICATION,
                        avant, nouvelleQuantite);
            }
        }
        piecesIncluses.add(new PieceIncluse(this, pieceIncluse, nouvelleQuantite));
        return new ChangementPieceIncluse(this, pieceIncluse, EvenementPieceIncluse.AJOUT, null, nouvelleQuantite);
    }

    /** @throws PieceIncluseRefuseeException la ligne ne porte pas cette pièce */
    ChangementPieceIncluse retirerPieceIncluse(Piece pieceIncluse) {
        PieceIncluse retiree = piecesIncluses.stream().filter(i -> i.porte(pieceIncluse)).findFirst()
                .orElseThrow(PieceIncluseRefuseeException::absente);
        piecesIncluses.remove(retiree);
        return new ChangementPieceIncluse(this, retiree.getPiece(), EvenementPieceIncluse.RETRAIT,
                retiree.getQuantite(), null);
    }

    /** Copie de la composition, telle que la ligne la porte à sa création. */
    public List<ChangementPieceIncluse> copie() {
        return piecesIncluses.stream()
                .map(i -> new ChangementPieceIncluse(this, i.getPiece(), EvenementPieceIncluse.COPIE,
                        null, i.getQuantite()))
                .toList();
    }

    /**
     * Retrait de toutes les pièces avec la ligne. La trace ne pointe pas vers la ligne, qui
     * disparaît dans la même écriture.
     */
    public List<ChangementPieceIncluse> retraitAvecLaLigne() {
        return piecesIncluses.stream()
                .map(i -> new ChangementPieceIncluse(null, i.getPiece(), EvenementPieceIncluse.RETRAIT,
                        i.getQuantite(), null))
                .toList();
    }

    /** Ligne de piece detachee prelevee du catalogue. Les prix figent ceux du catalogue. */
    public LigneIntervention(Intervention intervention, Piece piece, int quantite,
                             boolean ajouteeEnCours) {
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        this.piece = Objects.requireNonNull(piece, "piece");
        this.unite = UniteLigne.PIECE;
        this.quantite = quantiteValide(BigDecimal.valueOf(quantite), unite);
        this.libelleFige = piece.libelle(intervention.langueDesLibelles()).texte();
        this.prixUnitaireHtva = piece.getPrixHtva();
        this.tauxTva = piece.getTauxTva();
        this.ajouteeEnCours = ajouteeEnCours;
    }

    /** Plus grande quantité que porte {@code NUMERIC(7,2)}. */
    private static final BigDecimal QUANTITE_MAXIMALE = new BigDecimal("99999.99");

    /**
     * Quantité ramenée à deux décimales, après les contrôles de V73 : strictement positive,
     * entière sauf pour une ligne à l'heure, sous la borne de la colonne.
     */
    private static BigDecimal quantiteValide(BigDecimal quantite, UniteLigne unite) {
        Objects.requireNonNull(quantite, "quantite");
        if (quantite.signum() <= 0) {
            throw QuantiteLigneRefuseeException.nonPositive();
        }
        BigDecimal reduite = quantite.stripTrailingZeros();
        if (!unite.admetDesDecimales() && reduite.scale() > 0) {
            throw QuantiteLigneRefuseeException.nonEntiere();
        }
        if (reduite.scale() > 2 || quantite.compareTo(QUANTITE_MAXIMALE) > 0) {
            throw QuantiteLigneRefuseeException.tropPrecise();
        }
        return quantite.setScale(2);
    }

    /**
     * Nombre de pièces que la ligne retire du stock à la clôture. Seul point où la quantité
     * d'une ligne devient un nombre de pièces : la quantité décimale (H4-1b) ne changera
     * que lui.
     */
    public int quantitePourLeStock() {
        // Entière par construction et par ck_ligne_interv_quantite_unite : exacte, ou erreur.
        return quantite.intValueExact();
    }

    /** Type deduit du champ non-null. */
    public TypeLigneIntervention getType() {
        return prestation != null ? TypeLigneIntervention.MAIN_OEUVRE : TypeLigneIntervention.PIECE;
    }

    public BigDecimal totalHtva() {
        return prixUnitaireHtva.multiply(quantite).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Total taxe comprise, taux figé à la création : prix unitaire affiché fois quantité,
     * arrondi au cent pour des heures (Q-H-35). Le total hors TVA ci-dessus reste la mesure du
     * devis (RM-15), qui compare des sommes hors TVA et n'est pas touché par l'arrondi du prix
     * taxe comprise.
     */
    public BigDecimal totalTvac() {
        return CalculTva.totalTvac(prixUnitaireHtva, tauxTva, quantite);
    }

    /**
     * La ligne sort du total facturable en attendant l accord du membre (RM-15) :
     * la question est posee, aucune reponse n est encore donnee.
     */
    void mettreEnAttente() {
        this.accordMembre = null;
    }

    /** Le membre accepte cette ligne : elle rejoint le total facturable. */
    void accepter() {
        this.accordMembre = Boolean.TRUE;
    }

    /**
     * Le membre ecarte cette ligne. Elle reste dans le dossier (elle documente le
     * defaut constate par le garage) mais sort definitivement du total facturable
     * et ne sera pas executee.
     */
    void refuser() {
        this.accordMembre = Boolean.FALSE;
    }

    // --- etats derives du couple (ajouteeEnCours, accordMembre) ---------------------
    //
    // Aucun de ces quatre etats n est stocke : ils se lisent tous du couple, ce qui
    // rend impossible la divergence entre un drapeau et la realite. Ils sont exclusifs
    // et exhaustifs : toute ligne est dans exactement un des quatre.

    /**
     * Ligne issue du rendez-vous : elle compose le devis initial, accepte par le
     * membre a la reservation. Hors dispositif RM-15 : on ne redemande pas un accord
     * sur ce qui a deja ete commande. Le CHECK {@code ck_ligne_interv_accord}
     * garantit qu une telle ligne ne porte jamais d accord.
     */
    public boolean estDuDevisInitial() {
        return !ajouteeEnCours;
    }

    /** Ajout du garage soumis au membre, sans reponse a ce jour. */
    public boolean estEnAttenteValidation() {
        return ajouteeEnCours && accordMembre == null;
    }

    /** Ajout du garage accepte par le membre. */
    public boolean estAcceptee() {
        return ajouteeEnCours && Boolean.TRUE.equals(accordMembre);
    }

    /** Ajout du garage refuse par le membre : conserve au dossier, hors total. */
    public boolean estRefusee() {
        return ajouteeEnCours && Boolean.FALSE.equals(accordMembre);
    }

    /**
     * La ligne entre-t-elle dans le total facturable ? Le devis initial y entre
     * toujours. Un ajout n y entre qu une fois accepte.
     *
     * <p>Sont donc exclues les lignes <b>en attente</b> et les lignes <b>refusees</b>,
     * pour des raisons differentes : la premiere n est pas encore acquise, la seconde
     * ne le sera jamais.</p>
     */
    public boolean estFacturable() {
        return estDuDevisInitial() || estAcceptee();
    }

    public Long getId() { return id; }
    public Intervention getIntervention() { return intervention; }
    public Prestation getPrestation() { return prestation; }
    public Piece getPiece() { return piece; }
    public String getLibelleFige() { return libelleFige; }
    public BigDecimal getQuantite() { return quantite; }
    public UniteLigne getUnite() { return unite; }
    public BigDecimal getPrixUnitaireHtva() { return prixUnitaireHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public boolean isAjouteeEnCours() { return ajouteeEnCours; }
    public Boolean getAccordMembre() { return accordMembre; }
    public Optional<ChoixPieces> getChoixPieces() { return Optional.ofNullable(choixPieces); }
    public List<PieceIncluse> getPiecesIncluses() { return Collections.unmodifiableList(piecesIncluses); }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof LigneIntervention ligne)) return false;
        return id != null && id.equals(ligne.id);
    }

    @Override
    public int hashCode() {
        return LigneIntervention.class.hashCode();
    }
}
