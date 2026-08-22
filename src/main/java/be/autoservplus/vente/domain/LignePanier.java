package be.autoservplus.vente.domain;

import be.autoservplus.catalogue.domain.Piece;
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
 * un changement de catalogue ulterieur ne touche pas un panier existant (RM-30,
 * meme regle que {@code LigneRdv} et {@code LigneIntervention}).
 *
 * <p>Pas de suppression logique : la ligne n existe que par son panier, un retrait
 * passe par {@code orphanRemoval} de la relation parente.</p>
 *
 * <p>La table {@code ligne_panier} sert aussi aux commandes (colonnes
 * {@code commande_id} et {@code service_id}, en XOR avec {@code panier_id} et
 * {@code piece_id} respectivement). Ces deux colonnes, nullables, ne sont
 * volontairement pas mappees en V1 : la ligne de service (F12) et la conversion
 * panier vers commande (F14) sont hors perimetre de ce bloc — le mapping pourra
 * etre ajoute sans toucher au schema le moment venu.</p>
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

    // panier_id et piece_id sont NULLABLES en base (XOR avec commande_id et service_id
    // respectivement) : le mapping reste aussi permissif que le schema pour ne pas
    // bloquer la reaffectation panier -> commande prevue au dictionnaire. En V1, le
    // constructeur garantit que les deux sont toujours poses.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panier_id")
    private Panier panier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id")
    private Piece piece;

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

    /** Fusion d un doublon (F13) : la quantite s ajoute, les conditions figees restent. */
    void augmenterQuantite(int supplement) {
        exigerQuantiteValide(supplement);
        exigerQuantiteValide(this.quantite + supplement);
        this.quantite = (short) (this.quantite + supplement);
    }

    void changerQuantite(int nouvelleQuantite) {
        exigerQuantiteValide(nouvelleQuantite);
        this.quantite = (short) nouvelleQuantite;
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
    public Piece getPiece() { return piece; }
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
