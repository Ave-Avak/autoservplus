package be.autoservplus.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Socle commun a toutes les entites metier.
 *
 * <p>Porte les quatre colonnes d audit et les deux colonnes de suppression logique.
 * Aucune entite metier n est supprimee physiquement : les entites concernees filtrent
 * par defaut sur {@code deleted_at IS NULL}.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 120)
    private String deletedBy;

    /** Marque l entite comme supprimee sans la retirer de la base. */
    public void marquerSupprime(String auteur) {
        this.deletedAt = Instant.now();
        this.deletedBy = auteur;
    }

    public boolean estSupprime() {
        return deletedAt != null;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
}