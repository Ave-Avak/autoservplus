package be.autoservplus.identite.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Mot de passe changé par le titulaire depuis son profil. */
public class ChangementMotDePasseForm {

    @NotBlank(message = "{mot-de-passe-changement.actuel.obligatoire}")
    private String actuel;

    @NotBlank(message = "{validation.motDePasse.obligatoire}")
    @MotDePasseSolide
    private String motDePasse;

    @NotBlank(message = "{validation.motDePasse.confirmation}")
    private String confirmation;

    public boolean concordent() {
        return motDePasse != null && motDePasse.equals(confirmation);
    }

    public String getActuel() { return actuel; }
    public void setActuel(String actuel) { this.actuel = actuel; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
}
