package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motif saisi par l administrateur pour un refus ou une annulation de rendez-vous.
 * Le domaine exige un motif non blank. On le rejette cote formulaire pour un message
 * lisible avant meme d atteindre l entite.
 */
public class MotifForm {

    /** Borne commune aux motifs du garage (refus, annulation, déplacement), repris dans le courriel. */
    public static final int LONGUEUR_MAX = 500;

    @NotBlank(message = "{admin.rdv.motif.obligatoire}")
    @Size(max = LONGUEUR_MAX, message = "{admin.rdv.motif.longueur}")
    private String motif;

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
