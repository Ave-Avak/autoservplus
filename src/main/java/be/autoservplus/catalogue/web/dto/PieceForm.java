package be.autoservplus.catalogue.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Formulaire de creation (A4) et de modification (A5) d une piece.
 *
 * <p>Memes principes que {@link PrestationForm} : cles i18n pour les messages,
 * et {@code referenceFabricant} saisissable uniquement a la creation (ancre
 * d unicite, en champ cache a l edition, ignoree par le service).</p>
 */
public class PieceForm {

    @NotBlank(message = "{admin.catalogue.validation.categorie}")
    private String codeCategorie;

    @NotBlank(message = "{admin.catalogue.validation.reference-fabricant}")
    @Size(max = 60, message = "{admin.catalogue.validation.reference-fabricant}")
    private String referenceFabricant;

    @NotBlank(message = "{admin.catalogue.validation.libelle}")
    @Size(max = 150, message = "{admin.catalogue.validation.libelle}")
    private String libelle;

    @Size(max = 80, message = "{admin.catalogue.validation.marque}")
    private String marque;

    private String description;

    @NotNull(message = "{admin.catalogue.validation.prix}")
    @DecimalMin(value = "0.00", message = "{admin.catalogue.validation.prix}")
    @Digits(integer = 8, fraction = 2, message = "{admin.catalogue.validation.prix}")
    private BigDecimal prixHtva;

    @NotNull(message = "{admin.catalogue.validation.taux}")
    private BigDecimal tauxTva = new BigDecimal("21.00");

    @NotNull(message = "{admin.catalogue.validation.stock}")
    @PositiveOrZero(message = "{admin.catalogue.validation.stock}")
    private Integer quantiteStock = 0;

    @NotNull(message = "{admin.catalogue.validation.stock}")
    @PositiveOrZero(message = "{admin.catalogue.validation.stock}")
    private Integer seuilAlerte = 0;

    private boolean actif = true;

    public String getCodeCategorie() { return codeCategorie; }
    public void setCodeCategorie(String codeCategorie) { this.codeCategorie = codeCategorie; }
    public String getReferenceFabricant() { return referenceFabricant; }
    public void setReferenceFabricant(String referenceFabricant) { this.referenceFabricant = referenceFabricant; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getMarque() { return marque; }
    public void setMarque(String marque) { this.marque = marque; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrixHtva() { return prixHtva; }
    public void setPrixHtva(BigDecimal prixHtva) { this.prixHtva = prixHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal tauxTva) { this.tauxTva = tauxTva; }
    public Integer getQuantiteStock() { return quantiteStock; }
    public void setQuantiteStock(Integer quantiteStock) { this.quantiteStock = quantiteStock; }
    public Integer getSeuilAlerte() { return seuilAlerte; }
    public void setSeuilAlerte(Integer seuilAlerte) { this.seuilAlerte = seuilAlerte; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
