package be.autoservplus.identite.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Premier mot de passe choisi a la connexion, quand celui en vigueur a ete remis. */
public class MotDePasseImposeForm {

    @NotBlank(message = "{validation.motDePasse.obligatoire}")
    @MotDePasseSolide
    private String motDePasse;

    @NotBlank(message = "{validation.motDePasse.confirmation}")
    private String confirmation;

    public boolean concordent() {
        return motDePasse != null && motDePasse.equals(confirmation);
    }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
}
