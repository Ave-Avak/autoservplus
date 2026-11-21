package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RdvForm {

    @NotNull(message = "{rdv.formulaire.vehicule.obligatoire}")
    private UUID vehicule;

    @NotEmpty(message = "{rdv.formulaire.prestations.obligatoire}")
    private List<UUID> prestations = new ArrayList<>();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @NotNull(message = "{rdv.formulaire.date.obligatoire}")
    private LocalDate date;

    @NotNull(message = "{rdv.formulaire.heure.obligatoire}")
    private String debut;

    @Size(max = 2000, message = "{rdv.formulaire.commentaire.longueur}")
    private String commentaire;

    public UUID getVehicule() { return vehicule; }
    public void setVehicule(UUID vehicule) { this.vehicule = vehicule; }
    public List<UUID> getPrestations() { return prestations; }
    public void setPrestations(List<UUID> prestations) { this.prestations = prestations; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getDebut() { return debut; }
    public void setDebut(String debut) { this.debut = debut; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}