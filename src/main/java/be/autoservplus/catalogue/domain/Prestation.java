package be.autoservplus.catalogue.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Prestation proposee par le garage : vidange, remplacement de plaquettes, diagnostic.
 *
 * <p>La classe se nomme Prestation et non Service afin d eviter toute confusion avec
 * l annotation Spring du meme nom. La table sous-jacente reste {@code service}.</p>
 */
@Entity
@Table(name = "service")
@SQLRestriction("deleted_at IS NULL")
public class Prestation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    @NotBlank
    @Size(max = 40)
    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "prix_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixHtva;

    @NotNull
    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva = new BigDecimal("21.00");

    @Positive
    @Column(name = "duree_minutes", nullable = false)
    private int dureeMinutes;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected Prestation() {
        // requis par JPA
    }

    public Prestation(Categorie categorie, String code, String libelle,
                      BigDecimal prixHtva, int dureeMinutes) {
        this.reference = UUID.randomUUID();
        this.categorie = Objects.requireNonNull(categorie, "categorie");
        this.code = Objects.requireNonNull(code, "code");
        this.libelle = Objects.requireNonNull(libelle, "libelle");
        this.prixHtva = Objects.requireNonNull(prixHtva, "prixHtva");
        this.dureeMinutes = dureeMinutes;
    }

    /**
     * Prix affiche au client, taxe comprise.
     *
     * <p>La regle RM-30 impose que les prix presentes au consommateur soient exprimes
     * toutes taxes comprises. Le calcul se fait en BigDecimal, jamais en virgule
     * flottante, afin d eviter tout ecart d arrondi sur une facture.</p>
     */
    public BigDecimal prixTvac() {
        BigDecimal coefficient = BigDecimal.ONE.add(
                tauxTva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return prixHtva.multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
    }

    /** Montant de la taxe pour une unite. */
    public BigDecimal montantTva() {
        return prixTvac().subtract(prixHtva);
    }

    public void modifierPrix(BigDecimal nouveauPrixHtva) {
        if (nouveauPrixHtva == null || nouveauPrixHtva.signum() < 0) {
            throw new IllegalArgumentException("Le prix ne peut pas etre negatif.");
        }
        this.prixHtva = nouveauPrixHtva;
    }

    public void renommer(String nouveauLibelle) {
        this.libelle = Objects.requireNonNull(nouveauLibelle, "libelle");
    }

    /**
     * Deplace la prestation vers une autre categorie.
     *
     * @throws IllegalArgumentException si la categorie visee est destinee aux pieces
     */
    public void changerCategorie(Categorie nouvelleCategorie) {
        Objects.requireNonNull(nouvelleCategorie, "categorie");
        if (nouvelleCategorie.getType() != TypeCategorie.SERVICE) {
            throw new IllegalArgumentException(
                    "La categorie « %s » est destinee aux pieces, pas aux prestations."
                            .formatted(nouvelleCategorie.getCode()));
        }
        this.categorie = nouvelleCategorie;
    }

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Categorie getCategorie() { return categorie; }
    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrixHtva() { return prixHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal tauxTva) { this.tauxTva = TauxTvaBelge.verifier(tauxTva); }
    public int getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Prestation prestation)) return false;
        return id != null && id.equals(prestation.id);
    }

    @Override
    public int hashCode() {
        return Prestation.class.hashCode();
    }
}