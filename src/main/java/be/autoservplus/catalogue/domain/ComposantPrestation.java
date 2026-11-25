package be.autoservplus.catalogue.domain;

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
 * Pièce consommée par une prestation quand le garage la fournit, avec sa quantité (V76).
 *
 * <p>Sans suppression logique, à la différence des entités métier : un composant n'existe
 * que par sa prestation, et un composant retiré n'a plus rien à protéger. L'historique du
 * catalogue garde la composition avant et après chaque changement.</p>
 */
@Entity
@Table(name = "composition_prestation")
@EntityListeners(AuditingEntityListener.class)
public class ComposantPrestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private Prestation prestation;

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

    protected ComposantPrestation() {
        // requis par JPA
    }

    ComposantPrestation(Prestation prestation, Piece piece, int quantite) {
        this.prestation = Objects.requireNonNull(prestation, "prestation");
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
    public Prestation getPrestation() { return prestation; }
    public Piece getPiece() { return piece; }
    public int getQuantite() { return quantite; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof ComposantPrestation composant)) return false;
        return id != null && id.equals(composant.id);
    }

    @Override
    public int hashCode() {
        return ComposantPrestation.class.hashCode();
    }
}
