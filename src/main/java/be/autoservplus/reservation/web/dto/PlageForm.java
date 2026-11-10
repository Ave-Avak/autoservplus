package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;

/**
 * Formulaire d'une plage d'ouverture (A24). Le jour suit la norme ISO 8601, 1 pour
 * lundi, comme {@code plage_ouverture.jour_semaine}. Les heures sont locales au garage,
 * au format de {@code <input type="time">}, sans fuseau.
 */
public class PlageForm {

    @NotNull(message = "{admin.planning.validation.jour}")
    @Min(value = 1, message = "{admin.planning.validation.jour}")
    @Max(value = 7, message = "{admin.planning.validation.jour}")
    private Integer jour;

    @NotNull(message = "{admin.planning.validation.heure}")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    @NotNull(message = "{admin.planning.validation.heure}")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    private boolean actif = true;

    public Integer getJour() { return jour; }
    public void setJour(Integer jour) { this.jour = jour; }
    public LocalTime getHeureDebut() { return heureDebut; }
    public void setHeureDebut(LocalTime heureDebut) { this.heureDebut = heureDebut; }
    public LocalTime getHeureFin() { return heureFin; }
    public void setHeureFin(LocalTime heureFin) { this.heureFin = heureFin; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
