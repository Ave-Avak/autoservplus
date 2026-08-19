package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.Langue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Donnees saisies au formulaire d inscription.
 *
 * <p>Objet de transfert : la couche web ne manipule jamais l entite Utilisateur
 * directement. Cette separation evite d exposer l empreinte du mot de passe dans une
 * vue et permettra de servir la meme donnee en JSON le jour ou une API sera ajoutee.</p>
 */
public class InscriptionForm {

    @NotBlank(message = "{validation.email.obligatoire}")
    @Email(message = "{validation.email.format}")
    @Size(max = 180)
    private String email;

    @NotBlank(message = "{validation.motDePasse.obligatoire}")
    @Size(min = 12, max = 100, message = "{validation.motDePasse.longueur}")
    private String motDePasse;

    @NotBlank(message = "{validation.motDePasse.confirmation}")
    private String confirmationMotDePasse;

    @NotBlank(message = "{validation.nom.obligatoire}")
    @Size(max = 80)
    private String nom;

    @NotBlank(message = "{validation.prenom.obligatoire}")
    @Size(max = 80)
    private String prenom;

    private Langue langue = Langue.fr;

    /** Vrai lorsque les deux saisies du mot de passe concordent. */
    public boolean motsDePasseConcordent() {
        return motDePasse != null && motDePasse.equals(confirmationMotDePasse);
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public String getConfirmationMotDePasse() { return confirmationMotDePasse; }
    public void setConfirmationMotDePasse(String c) { this.confirmationMotDePasse = c; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public Langue getLangue() { return langue; }
    public void setLangue(Langue langue) { this.langue = langue; }
}