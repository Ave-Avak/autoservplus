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

    @NotNull(message = "Choisissez un véhicule.")
    private UUID vehicule;

    @NotEmpty(message = "Choisissez au moins une prestation.")
    private List<UUID> prestations = new ArrayList<>();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @NotNull(message = "Choisissez une date.")
    private LocalDate date;

    @NotNull(message = "Choisissez une heure.")
    private String debut;

    @Size(max = 2000, message = "Le commentaire ne peut pas dépasser 2000 caractères.")
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