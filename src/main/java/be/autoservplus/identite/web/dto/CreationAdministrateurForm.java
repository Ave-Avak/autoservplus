package be.autoservplus.identite.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creation d un administrateur par le super-administrateur. Aucun champ de mot de passe,
 * et c est le point : le titulaire choisira le sien depuis le lien qu il recevra.
 */
public class CreationAdministrateurForm {

    @NotBlank(message = "{validation.email.obligatoire}")
    @Email(message = "{validation.email.format}")
    @Size(max = 180, message = "{validation.email.longueur}")
    private String email;

    @NotBlank(message = "{validation.prenom.obligatoire}")
    @Size(max = 80, message = "{validation.prenom.obligatoire}")
    private String prenom;

    @NotBlank(message = "{validation.nom.obligatoire}")
    @Size(max = 80, message = "{validation.nom.obligatoire}")
    private String nom;

    @Size(max = 80, message = "{admin.administrateurs.validation.fonction}")
    private String fonction;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }
}
