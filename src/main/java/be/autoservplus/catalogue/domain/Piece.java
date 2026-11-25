package be.autoservplus.catalogue.domain;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Piece detachee proposee a la vente ou montee lors d une intervention.
 *
 * <p>Le stock est suivi ici, avec un seuil d'alerte qui déclenche un signalement au
 * gérant. Rien n'est réservé : le stock descend au paiement d'une commande (module vente)
 * et à la clôture d'une intervention, pour les pièces réellement posées
 * ({@code PieceRepository#retirerAuPlus}).</p>
 */
@Entity
@Table(name = "piece")
@SQLRestriction("deleted_at IS NULL")
public class Piece extends BaseEntity {

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
    @Size(max = 60)
    @Column(name = "reference_fabricant", nullable = false, length = 60, unique = true)
    private String referenceFabricant;

    @NotBlank
    @Size(max = 150)
    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Valid
    @Embedded
    private Traductions traductions = Traductions.aucune();

    @Size(max = 80)
    @Column(name = "marque", length = 80)
    private String marque;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "prix_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixHtva;

    @NotNull
    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva = new BigDecimal("21.00");

    @PositiveOrZero
    @Column(name = "quantite_stock", nullable = false)
    private int quantiteStock;

    @PositiveOrZero
    @Column(name = "seuil_alerte", nullable = false)
    private int seuilAlerte;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected Piece() {
        // requis par JPA
    }

    public Piece(Categorie categorie, String referenceFabricant, String libelle, BigDecimal prixHtva) {
        this.reference = UUID.randomUUID();
        this.categorie = Objects.requireNonNull(categorie, "categorie");
        this.referenceFabricant = Objects.requireNonNull(referenceFabricant, "referenceFabricant");
        this.libelle = Objects.requireNonNull(libelle, "libelle");
        this.prixHtva = Objects.requireNonNull(prixHtva, "prixHtva");
    }

    /**
     * Prix affiché au client, taxe comprise (RM-30). C'est le nombre même qui sert au total
     * d'une ligne : le prix annoncé est le prix payé.
     */
    public BigDecimal prixTvac() {
        return CalculTva.unitaireTvac(prixHtva, tauxTva);
    }

    public boolean estDisponible() {
        return actif && quantiteStock > 0;
    }

    public boolean stockSousLeSeuil() {
        return quantiteStock <= seuilAlerte;
    }

    /**
     * Retire des unites du stock.
     *
     * @throws IllegalArgumentException si la quantite demandee depasse le stock disponible
     */
    public void retirerDuStock(int quantite) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantite doit etre strictement positive.");
        }
        if (quantite > quantiteStock) {
            throw new IllegalArgumentException(
                    "Stock insuffisant : %d demandees, %d disponibles.".formatted(quantite, quantiteStock));
        }
        this.quantiteStock -= quantite;
    }

    public void ajouterAuStock(int quantite) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantite doit etre strictement positive.");
        }
        this.quantiteStock += quantite;
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
     * Deplace la piece vers une autre categorie.
     *
     * @throws IllegalArgumentException si la categorie visee est destinee aux prestations
     */
    public void changerCategorie(Categorie nouvelleCategorie) {
        Objects.requireNonNull(nouvelleCategorie, "categorie");
        if (nouvelleCategorie.getType() != TypeCategorie.PIECE) {
            throw new IllegalArgumentException(
                    "La categorie « %s » est destinee aux prestations, pas aux pieces."
                            .formatted(nouvelleCategorie.getCode()));
        }
        this.categorie = nouvelleCategorie;
    }

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Categorie getCategorie() { return categorie; }
    public String getReferenceFabricant() { return referenceFabricant; }
    public String getLibelle() { return libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Libellé dans la langue demandée, ou en français s'il n'y est pas traduit. */
    public TexteLocalise libelle(Locale langue) {
        return TexteLocalise.choisir(langue, libelle, getTraductions().getLibelleNl(), getTraductions().getLibelleEn());
    }

    /** Description dans la langue demandée, ou en français si elle n'y est pas traduite. */
    public TexteLocalise description(Locale langue) {
        return TexteLocalise.choisir(langue, description,
                getTraductions().getDescriptionNl(), getTraductions().getDescriptionEn());
    }

    /** Jamais {@code null} : Hibernate rend un composant nul quand toutes ses colonnes le sont. */
    public Traductions getTraductions() {
        return traductions == null ? Traductions.aucune() : traductions;
    }

    public void traduire(Traductions nouvelles) {
        this.traductions = Objects.requireNonNull(nouvelles, "traductions");
    }
    public String getMarque() { return marque; }
    public void setMarque(String marque) { this.marque = marque; }
    public BigDecimal getPrixHtva() { return prixHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal tauxTva) { this.tauxTva = TauxTvaBelge.verifier(tauxTva); }
    public int getQuantiteStock() { return quantiteStock; }
    public void setQuantiteStock(int quantiteStock) { this.quantiteStock = quantiteStock; }
    public int getSeuilAlerte() { return seuilAlerte; }
    public void setSeuilAlerte(int seuilAlerte) { this.seuilAlerte = seuilAlerte; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Piece piece)) return false;
        return id != null && id.equals(piece.id);
    }

    @Override
    public int hashCode() {
        return Piece.class.hashCode();
    }
}