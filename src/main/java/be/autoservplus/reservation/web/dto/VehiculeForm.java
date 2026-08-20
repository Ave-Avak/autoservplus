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

    @NotBlank(message = "La plaque d'immatriculation est obligatoire.")
    @Size(max = 15)
    private String plaque;

    @NotBlank(message = "La marque est obligatoire.")
    @Size(max = 60)
    private String marque;

    @NotBlank(message = "Le modèle est obligatoire.")
    @Size(max = 80)
    private String modele;

    @NotNull(message = "La motorisation est obligatoire.")
    private Motorisation motorisation;

    @Min(value = 1900, message = "L'année doit être postérieure à 1900.")
    @Max(value = 2100, message = "L'année semble erronée.")
    private Short annee;

    @PositiveOrZero(message = "Le kilométrage ne peut pas être négatif.")
    private Integer kilometrage;

    @Size(max = 20)
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