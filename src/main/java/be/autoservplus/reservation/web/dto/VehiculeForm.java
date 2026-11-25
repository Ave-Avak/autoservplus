package be.autoservplus.reservation.web.dto;

import be.autoservplus.reservation.domain.Motorisation;
import jakarta.validation.constraints.*;

/**
 * Donnees saisies aux formulaires d ajout et de modification d un vehicule.
 *
 * <p>La plaque n est renseignee qu a l ajout : elle identifie le vehicule et n est pas
 * modifiable ensuite.</p>
 */
public class VehiculeForm {

    @NotBlank(message = "{vehicule.validation.plaque}")
    @Size(max = 15)
    private String plaque;

    @NotBlank(message = "{vehicule.validation.marque}")
    @Size(max = 60)
    private String marque;

    @NotBlank(message = "{vehicule.validation.modele}")
    @Size(max = 80)
    private String modele;

    @NotNull(message = "{vehicule.validation.motorisation}")
    private Motorisation motorisation;

    @Min(value = 1900, message = "{vehicule.validation.annee.min}")
    @Max(value = 2100, message = "{vehicule.validation.annee.max}")
    private Short annee;

    @PositiveOrZero(message = "{vehicule.validation.kilometrage}")
    private Integer kilometrage;

    @Size(max = 20, message = "{vehicule.validation.chassis.longueur}")
    private String numeroChassis;

    public String getPlaque() { return plaque; }
    public void setPlaque(String plaque) { this.plaque = plaque; }
    public String getMarque() { return marque; }
    public void setMarque(String marque) { this.marque = marque; }
    public String getModele() { return modele; }
    public void setModele(String modele) { this.modele = modele; }
    public Motorisation getMotorisation() { return motorisation; }
    public void setMotorisation(Motorisation motorisation) { this.motorisation = motorisation; }
    public Short getAnnee() { return annee; }
    public void setAnnee(Short annee) { this.annee = annee; }
    public Integer getKilometrage() { return kilometrage; }
    public void setKilometrage(Integer kilometrage) { this.kilometrage = kilometrage; }
    public String getNumeroChassis() { return numeroChassis; }
    public void setNumeroChassis(String numeroChassis) { this.numeroChassis = numeroChassis; }
}