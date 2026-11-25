package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Piece;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Pièce comprise dans le prix d'une ligne de forfait dont le garage fournit les pièces (V77).
 *
 * <p>Jamais facturée, jamais sur une facture : son prix est celui du forfait. Elle sort du
 * stock à la clôture avec les lignes de pièce ({@code InterventionService.decompterLeStock}).
 * Copiée de la composition de la prestation à la création de la ligne, puis modifiable par
 * le garage tant que l'intervention n'est ni terminée ni annulée, chaque changement écrit
 * dans {@code historique_piece_incluse}.</p>
 *
 * <p>Pas de suppression logique, comme la composition : elle n'existe que par sa ligne, et
 * le journal garde ce qu'elle a été.</p>
 */
@Entity
@Table(name = "piece_incluse")
@EntityListeners(AuditingEntityListener.class)
public class PieceIncluse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ligne_intervention_id", nullable = false, updatable = false)
    private LigneIntervention ligne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piece_id", nullable = false, updatable = false)
    private Piece piece;

    @Column(name = "quantite", nullable = false)
    private int quantite;

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

    protected PieceIncluse() {
        // requis par JPA
    }

    PieceIncluse(LigneIntervention ligne, Piece piece, int quantite) {
        this.ligne = Objects.requireNonNull(ligne, "ligne");
        this.piece = Objects.requireNonNull(piece, "piece");
        this.quantite = quantite;
    }

    void changerQuantite(int nouvelle) {
        this.quantite = nouvelle;
    }

    /** Même instance, ou même pièce en base : une pièce pas encore enregistrée n'a pas d'identifiant. */
    boolean porte(Piece autre) {
        return piece == autre || piece.equals(autre);
    }

    public Long getId() { return id; }
    public LigneIntervention getLigne() { return ligne; }
    public Piece getPiece() { return piece; }
    public int getQuantite() { return quantite; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof PieceIncluse incluse)) return false;
        return id != null && id.equals(incluse.id);
    }

    @Override
    public int hashCode() {
        return PieceIncluse.class.hashCode();
    }
}
