package be.autoservplus.reservation.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.util.Objects;
import java.util.UUID;

/**
 * Ressource planifiable de l atelier : pont, baie de diagnostic, poste de montage.
 *
 * <p>La capacite de l atelier est le nombre de postes actifs. Un rendez-vous occupe un
 * poste sur un intervalle. Le modele accueille sans modification structurelle le
 * rattachement ulterieur d un mecanicien ou de competences.</p>
 */
@Entity
@Table(name = "poste_atelier")
@SQLRestriction("deleted_at IS NULL")
public class PosteAtelier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotBlank
    @Size(max = 80)
    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected PosteAtelier() {
        // requis par JPA
    }

    public PosteAtelier(String libelle) {
        this.reference = UUID.randomUUID();
        this.libelle = Objects.requireNonNull(libelle, "libelle").trim();
    }

    public void renommer(String libelle) { this.libelle = Objects.requireNonNull(libelle, "libelle").trim(); }
    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getLibelle() { return libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public short getOrdre() { return ordre; }
    public void setOrdre(short ordre) { this.ordre = ordre; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof PosteAtelier poste)) return false;
        return id != null && id.equals(poste.id);
    }

    @Override
    public int hashCode() {
        return PosteAtelier.class.hashCode();
    }
}