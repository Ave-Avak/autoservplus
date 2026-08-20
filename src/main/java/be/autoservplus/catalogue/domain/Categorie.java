package be.autoservplus.catalogue.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.util.Objects;

/**
 * Regroupement de prestations ou de pieces detachees dans le catalogue.
 *
 * <p>Correspond a la table {@code categorie}. Les instances supprimees logiquement sont
 * exclues de toutes les requetes par {@link SQLRestriction}.</p>
 */
@Entity
@Table(name = "categorie")
@SQLRestriction("deleted_at IS NULL")
public class Categorie extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(name = "libelle", nullable = false, length = 120)
    private String libelle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeCategorie type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected Categorie() {
        // requis par JPA
    }

    public Categorie(String code, String libelle, TypeCategorie type) {
        this.code = Objects.requireNonNull(code, "code");
        this.libelle = Objects.requireNonNull(libelle, "libelle");
        this.type = Objects.requireNonNull(type, "type");
    }

    public void renommer(String nouveauLibelle) {
        this.libelle = Objects.requireNonNull(nouveauLibelle, "libelle");
    }

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
    public TypeCategorie getType() { return type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public short getOrdre() { return ordre; }
    public void setOrdre(short ordre) { this.ordre = ordre; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Categorie categorie)) return false;
        return id != null && id.equals(categorie.id);
    }

    @Override
    public int hashCode() {
        return Categorie.class.hashCode();
    }
}