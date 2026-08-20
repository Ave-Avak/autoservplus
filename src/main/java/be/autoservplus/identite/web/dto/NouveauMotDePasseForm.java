package be.autoservplus.identite.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Saisie d un nouveau mot de passe depuis un lien de reinitialisation. */
public class NouveauMotDePasseForm {

    @NotBlank
    private String jeton;

    @NotBlank
    @Size(min = 12, max = 100, message = "Le mot de passe doit comporter au moins douze caractères.")
    private String motDePasse;

    @NotBlank
    private String confirmation;

    public boolean concordent() {
        return motDePasse != null && motDePasse.equals(confirmation);
    }

    public String getJeton() { return jeton; }
    public void setJeton(String jeton) { this.jeton = jeton; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
}