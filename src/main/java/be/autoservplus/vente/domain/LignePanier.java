package be.autoservplus.vente.domain;

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
import java.util.Objects;

/**
 * Ligne d un panier : une piece detachee et sa quantite, aux conditions <b>figees
 * a l ajout</b> (libelle, prix unitaire HTVA, taux TVA copies depuis la piece) —
 * un changement de catalogue ulterieur ne touche pas un panier existant. Invariant
 * de conception, meme recopie que {@code LigneRdv} et {@code LigneIntervention} ;
 * aucun numero de regle au CdC ne couvre le figeage lui-meme.
 *
 * <p>Pas de suppression logique : la ligne n existe que par son panier, un retrait
 * passe par {@code orphanRemoval} de la relation parente.</p>
 *
 * <p>La table {@code ligne_panier} sert aussi aux commandes : a la conversion
 * (F14), la meme ligne passe de {@code panier_id} a {@code commande_id} via
 * {@link #rattacherA} — pas de recopie, les valeurs figees voyagent telles
 * quelles, le CHECK {@code ck_ligne_rattachement_unique} garantit le XOR.
 * La colonne {@code service_id} (ligne de service, F12) reste volontairement
 * non mappee dans ce bloc.</p>
 */
@Entity
@Table(name = "ligne_panier")
@EntityListeners(AuditingEntityListener.class)
public class LignePanier {

    /** Borne physique de la colonne SMALLINT — refusee avant tout debordement. */
    private static final int QUANTITE_MAX = Short.MAX_VALUE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // panier_id, commande_id et piece_id sont NULLABLES en base (XOR panier/commande
    // et piece/service) : le mapping reste aussi permissif que le schema. Le
    // constructeur garantit qu une ligne nait avec panier et piece poses ; la
    // conversion (rattacherA) bascule ensuite panier -> commande.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panier_id")
    private Panier panier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id")
    private Piece piece;

    /**
     * Prestation vendue (F12). La colonne {@code service_id} existait au socle V4,
     * volontairement non mappee tant que le service ne passait pas par le panier.
     * Le CHECK {@code ck_ligne_article_unique} impose deja le XOR avec {@code piece}
     * en base : une ligne est d une nature ou de l autre, jamais des deux.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Prestation prestation;

    @NotNull
    @Column(name = "libelle_fige", nullable = false, length = 150)
    private String libelleFige;

    @Column(name = "quantite", nullable = false)
    private short quantite;

    @Column(name = "prix_unitaire_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaireHtva;

    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

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

    protected LignePanier() {
        // requis par JPA
    }

    LignePanier(Panier panier, Piece piece, int quantite) {
        this.panier = Objects.requireNonNull(panier, "panier");
        this.piece = Objects.requireNonNull(piece, "piece");
        exigerQuantiteValide(quantite);
        this.libelleFige = piece.getLibelle();
        this.prixUnitaireHtva = piece.getPrixHtva();
        this.tauxTva = piece.getTauxTva();
        this.quantite = (short) quantite;
    }

    /**
     * Ligne de prestation (F12). <b>Memes valeurs figees a l ajout</b> que pour une
     * piece (RM-30) : libelle, prix et taux sont recopies au moment ou le membre
     * ajoute, pas relus au passage en commande. Un tarif revu entre-temps ne change
     * pas ce que le membre a vu quand il a decide.
     */
    LignePanier(Panier panier, Prestation prestation, int quantite) {
        this.panier = Objects.requireNonNull(panier, "panier");
        this.prestation = Objects.requireNonNull(prestation, "prestation");
        exigerQuantiteValide(quantite);
        this.libelleFige = prestation.getLibelle();
        this.prixUnitaireHtva = prestation.getPrixHtva();
        this.tauxTva = prestation.getTauxTva();
        this.quantite = (short) quantite;
    }

    /** Nature de la ligne, lue sur l article reellement rattache. */
    public boolean estService() {
        return prestation != null;
    }

    /** Fusion d un doublon (F13) : la quantite s ajoute, les conditions figees restent. */
    void augmenterQuantite(int supplement) {
        exigerHorsCommande();
        exigerQuantiteValide(supplement);
        exigerQuantiteValide(this.quantite + supplement);
        this.quantite = (short) (this.quantite + supplement);
    }

    void changerQuantite(int nouvelleQuantite) {
        exigerHorsCommande();
        exigerQuantiteValide(nouvelleQuantite);
        this.quantite = (short) nouvelleQuantite;
    }

    /**
     * <b>Immuabilite comptable</b> : une ligne rattachee a une commande est une piece
     * comptable (conservee 7 ans), plus une ligne de panier. Toute mutation est
     * refusee ICI, dans l entite — pas par convention d appel : meme un appelant qui
     * tiendrait encore une reference (la collection du panier n est pas purgee dans
     * la transaction de conversion, voir {@link #rattacherA}) se heurte a la garde.
     */
    private void exigerHorsCommande() {
        if (commande != null) {
            throw new IllegalStateException(
                    "La ligne appartient a la commande %s : une piece comptable est immuable."
                            .formatted(commande.getNumero()));
        }
    }

    /**
     * Conversion F14 : la ligne quitte son panier pour la commande, valeurs figees
     * inchangees. Definitif — une ligne de commande ne revient jamais au panier
     * (le CHECK XOR garantit qu elle n appartient qu a un seul des deux).
     *
     * <p><b>Piege {@code orphanRemoval}</b> : la ligne ne doit PAS etre retiree de
     * la collection {@code Panier.lignes} dans la meme session — ce retrait la
     * marquerait orpheline et Hibernate la SUPPRIMERAIT physiquement, commande
     * comprise. Seul ce changement de FK fait foi ; le panier se recharge vide.</p>
     */
    void rattacherA(Commande commande) {
        exigerHorsCommande(); // definitif : une ligne ne change jamais de commande
        this.commande = Objects.requireNonNull(commande, "commande");
        this.panier = null;
    }

    private static void exigerQuantiteValide(int quantite) {
        if (quantite < 1) {
            throw new IllegalArgumentException("La quantite doit valoir au moins 1.");
        }
        if (quantite > QUANTITE_MAX) {
            throw new IllegalArgumentException(
                    "La quantite ne peut pas depasser %d.".formatted(QUANTITE_MAX));
        }
    }

    // --- montants (RM-30) ------------------------------------------------------------

    public BigDecimal totalHtva() {
        return prixUnitaireHtva.multiply(BigDecimal.valueOf(quantite))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Total TVAC de la ligne, au taux fige a l ajout. */
    public BigDecimal totalTvac() {
        BigDecimal coefficient = BigDecimal.ONE.add(
                tauxTva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return totalHtva().multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * TVA de la ligne, definie comme TVAC - HTVA plutot que recalculee : l identite
     * HTVA + TVA = TVAC tient alors par construction, centime d arrondi compris.
     */
    public BigDecimal totalTva() {
        return totalTvac().subtract(totalHtva());
    }

    public Long getId() { return id; }
    public Panier getPanier() { return panier; }
    public Commande getCommande() { return commande; }
    public Piece getPiece() { return piece; }
    public Prestation getPrestation() { return prestation; }
    public String getLibelleFige() { return libelleFige; }
    public short getQuantite() { return quantite; }
    public BigDecimal getPrixUnitaireHtva() { return prixUnitaireHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof LignePanier ligne)) return false;
        return id != null && id.equals(ligne.id);
    }

    @Override
    public int hashCode() {
        return LignePanier.class.hashCode();
    }
}
