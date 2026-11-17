package be.autoservplus.reservation.web.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Formulaire de déplacement par le garage : le jour regardé, l'heure choisie (instant
 * ISO), la version lue à l'ouverture de la page et le motif communiqué au membre.
 */
public class DeplacementRdvForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    private String debut;
    private Long version;
    private String motif;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getDebut() { return debut; }
    public void setDebut(String debut) { this.debut = debut; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
