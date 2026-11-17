package be.autoservplus.reservation.web.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Formulaire de déplacement : le jour regardé, l'heure choisie (instant ISO, comme à la
 * réservation) et la version du rendez-vous lue à l'ouverture de la page.
 */
public class ModificationRdvForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    private String debut;
    private Long version;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getDebut() { return debut; }
    public void setDebut(String debut) { this.debut = debut; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
