package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Formulaire des paramètres de réservation. Les bornes répètent celles de
 * {@code ParametreAtelier} pour un message sous le champ : l'entité reste le juge.
 */
public class ParametresReservationForm {

    private boolean confirmationAutomatique;

    @NotNull(message = "{admin.parametres.validation.delai-reponse}")
    @Min(value = 2, message = "{admin.parametres.validation.delai-reponse}")
    @Max(value = 168, message = "{admin.parametres.validation.delai-reponse}")
    private Integer delaiReponseHeures;

    @NotNull(message = "{admin.parametres.validation.delai-minimal}")
    @Min(value = 0, message = "{admin.parametres.validation.delai-minimal}")
    @Max(value = 168, message = "{admin.parametres.validation.delai-minimal}")
    private Integer delaiMinimalHeures;

    @NotNull(message = "{admin.parametres.validation.delai-annulation}")
    @Min(value = 0, message = "{admin.parametres.validation.delai-annulation}")
    @Max(value = 168, message = "{admin.parametres.validation.delai-annulation}")
    private Integer delaiAnnulationHeures;

    @NotNull(message = "{admin.parametres.validation.delai-rappel}")
    @Min(value = 0, message = "{admin.parametres.validation.delai-rappel}")
    @Max(value = 168, message = "{admin.parametres.validation.delai-rappel}")
    private Integer delaiRappelHeures;

    public boolean isConfirmationAutomatique() { return confirmationAutomatique; }
    public void setConfirmationAutomatique(boolean confirmationAutomatique) { this.confirmationAutomatique = confirmationAutomatique; }
    public Integer getDelaiReponseHeures() { return delaiReponseHeures; }
    public void setDelaiReponseHeures(Integer delaiReponseHeures) { this.delaiReponseHeures = delaiReponseHeures; }
    public Integer getDelaiMinimalHeures() { return delaiMinimalHeures; }
    public void setDelaiMinimalHeures(Integer delaiMinimalHeures) { this.delaiMinimalHeures = delaiMinimalHeures; }
    public Integer getDelaiAnnulationHeures() { return delaiAnnulationHeures; }
    public void setDelaiAnnulationHeures(Integer delaiAnnulationHeures) { this.delaiAnnulationHeures = delaiAnnulationHeures; }
    public Integer getDelaiRappelHeures() { return delaiRappelHeures; }
    public void setDelaiRappelHeures(Integer delaiRappelHeures) { this.delaiRappelHeures = delaiRappelHeures; }
}
