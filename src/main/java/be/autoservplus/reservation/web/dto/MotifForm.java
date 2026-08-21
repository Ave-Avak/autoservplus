package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motif saisi par l administrateur pour un refus ou une annulation de rendez-vous.
 * Le domaine exige un motif non blank ; on le rejette cote formulaire pour un message
 * lisible avant meme d atteindre l entite.
 */
public class MotifForm {

    @NotBlank(message = "Un motif est obligatoire.")
    @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères.")
    private String motif;

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
