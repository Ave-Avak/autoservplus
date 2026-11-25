package be.autoservplus.catalogue.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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

    @Size(max = 120)
    @Column(name = "libelle_nl", length = 120)
    private String libelleNl;

    @Size(max = 120)
    @Column(name = "libelle_en", length = 120)
    private String libelleEn;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeCategorie type;

    /**
     * Rayon du catalogue public, nul pour une catégorie de prestations
     * ({@code ck_categorie_rayon}). Il ne change que le rangement des articles.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rayon", length = 20)
    private Rayon rayon;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected Categorie() {
        // requis par JPA
    }

    /**
     * Une catégorie de produits créée sans rayon va aux pièces auto, comme les catégories
     * que V78 a rangées. {@link #ranger(Rayon)} la déplace.
     */
    public Categorie(String code, String libelle, TypeCategorie type) {
        this.code = Objects.requireNonNull(code, "code");
        this.libelle = Objects.requireNonNull(libelle, "libelle");
        this.type = Objects.requireNonNull(type, "type");
        this.rayon = type == TypeCategorie.PIECE ? Rayon.PIECES_AUTO : null;
    }

    /** Catégorie de produits rangée dans le rayon donné. */
    public Categorie(String code, String libelle, Rayon rayon) {
        this(code, libelle, TypeCategorie.PIECE);
        ranger(rayon);
    }

    /**
     * Change le rayon d'une catégorie de produits. Une catégorie de prestations n'a pas de
     * rayon : la base le refuse aussi.
     */
    public void ranger(Rayon nouveauRayon) {
        Objects.requireNonNull(nouveauRayon, "rayon");
        if (type != TypeCategorie.PIECE) {
            throw new IllegalStateException("Une catégorie de prestations n'a pas de rayon : " + code);
        }
        this.rayon = nouveauRayon;
    }

    public void renommer(String nouveauLibelle) {
        this.libelle = Objects.requireNonNull(nouveauLibelle, "libelle");
    }

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getLibelle() { return libelle; }

    /** Libellé dans la langue demandée, ou en français s'il n'y est pas traduit. */
    public TexteLocalise libelle(Locale langue) {
        return TexteLocalise.choisir(langue, libelle, libelleNl, libelleEn);
    }

    /** Traductions du libellé. Une saisie vide vaut « non traduit ». */
    public void traduire(String neerlandais, String anglais) {
        this.libelleNl = TexteLocalise.present(neerlandais) ? neerlandais.trim() : null;
        this.libelleEn = TexteLocalise.present(anglais) ? anglais.trim() : null;
    }

    public String getLibelleNl() { return libelleNl; }
    public String getLibelleEn() { return libelleEn; }
    public TypeCategorie getType() { return type; }

    /** Rayon d'une catégorie de produits, vide pour une catégorie de prestations. */
    public Optional<Rayon> getRayon() { return Optional.ofNullable(rayon); }
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