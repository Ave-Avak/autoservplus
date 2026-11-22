package be.autoservplus.catalogue.web.dto;

import be.autoservplus.catalogue.domain.Traductions;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Formulaire de creation (A1) et de modification (A2) d une prestation.
 *
 * <p>Les messages de contrainte sont des cles i18n resolues par le MessageSource
 * de l application (fichiers {@code i18n/messages*}), pas des chaines en dur. Le
 * champ {@code code} n est saisissable qu a la creation. En edition il voyage en
 * champ cache pour satisfaire la validation, et le service l ignore de toute
 * facon (identite technique immuable).</p>
 */
public class PrestationForm {

    @NotBlank(message = "{admin.catalogue.validation.categorie}")
    private String codeCategorie;

    @NotBlank(message = "{admin.catalogue.validation.code}")
    @Size(max = 40, message = "{admin.catalogue.validation.code}")
    private String code;

    @NotBlank(message = "{admin.catalogue.validation.libelle}")
    @Size(max = 150, message = "{admin.catalogue.validation.libelle}")
    private String libelle;

    private String description;

    @Size(max = 150, message = "{admin.catalogue.validation.traduction}")
    private String libelleNl;

    private String descriptionNl;

    @Size(max = 150, message = "{admin.catalogue.validation.traduction}")
    private String libelleEn;

    private String descriptionEn;

    @NotNull(message = "{admin.catalogue.validation.prix}")
    @DecimalMin(value = "0.00", message = "{admin.catalogue.validation.prix}")
    @Digits(integer = 8, fraction = 2, message = "{admin.catalogue.validation.prix}")
    private BigDecimal prixHtva;

    @NotNull(message = "{admin.catalogue.validation.taux}")
    private BigDecimal tauxTva = new BigDecimal("21.00");

    @NotNull(message = "{admin.catalogue.validation.duree}")
    @Positive(message = "{admin.catalogue.validation.duree}")
    private Integer dureeMinutes;

    private boolean actif = true;

    public String getCodeCategorie() { return codeCategorie; }
    public void setCodeCategorie(String codeCategorie) { this.codeCategorie = codeCategorie; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLibelleNl() { return libelleNl; }
    public void setLibelleNl(String libelleNl) { this.libelleNl = libelleNl; }
    public String getDescriptionNl() { return descriptionNl; }
    public void setDescriptionNl(String descriptionNl) { this.descriptionNl = descriptionNl; }
    public String getLibelleEn() { return libelleEn; }
    public void setLibelleEn(String libelleEn) { this.libelleEn = libelleEn; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    /** Les traductions saisies. Un champ laissé vide vaut « non traduit ». */
    public Traductions traductions() {
        return new Traductions(libelleNl, libelleEn, descriptionNl, descriptionEn);
    }

    public void reprendre(Traductions traductions) {
        this.libelleNl = traductions.getLibelleNl();
        this.libelleEn = traductions.getLibelleEn();
        this.descriptionNl = traductions.getDescriptionNl();
        this.descriptionEn = traductions.getDescriptionEn();
    }
    public BigDecimal getPrixHtva() { return prixHtva; }
    public void setPrixHtva(BigDecimal prixHtva) { this.prixHtva = prixHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal tauxTva) { this.tauxTva = tauxTva; }
    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
