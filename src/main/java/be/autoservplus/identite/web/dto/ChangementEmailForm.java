package be.autoservplus.identite.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande de changement d adresse (CdC 5.2.2).
 *
 * <p>Le mot de passe n est pas une donnee du profil : il re-authentifie l auteur du
 * geste. Il n est donc jamais reaffiche, et le formulaire repart vide apres un refus.</p>
 */
public class ChangementEmailForm {

    @NotBlank(message = "{validation.email.obligatoire}")
    @Email(message = "{validation.email.format}")
    @Size(max = 180, message = "{validation.email.longueur}")
    private String nouvelleAdresse;

    @NotBlank(message = "{validation.motDePasse.obligatoire}")
    private String motDePasse;

    public String getNouvelleAdresse() { return nouvelleAdresse; }
    public void setNouvelleAdresse(String nouvelleAdresse) { this.nouvelleAdresse = nouvelleAdresse; }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
}
