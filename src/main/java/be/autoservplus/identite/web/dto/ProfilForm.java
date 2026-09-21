package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Champs modifiables du profil membre.
 *
 * <p>L adresse de courriel n y figure pas : elle est l identifiant de connexion et
 * suit une procedure de verification propre.</p>
 *
 * <p>Les longueurs maximales reprennent <b>exactement</b> celles du schema (V1) : les
 * laisser diverger ferait echouer l enregistrement au niveau de la base, avec une
 * erreur technique la ou le formulaire aurait du dire ce qui depasse.</p>
 */
public class ProfilForm {

    @NotBlank(message = "{validation.prenom.obligatoire}")
    @Size(max = 80, message = "{validation.prenom.obligatoire}")
    private String prenom;

    @NotBlank(message = "{validation.nom.obligatoire}")
    @Size(max = 80, message = "{validation.nom.obligatoire}")
    private String nom;

    @Size(max = 30, message = "{validation.telephone.longueur}")
    private String telephone;

    @Size(max = 150, message = "{validation.adresse.rue}")
    private String rue;

    @Size(max = 15, message = "{validation.adresse.numero}")
    private String numeroRue;

    @Size(max = 10, message = "{validation.adresse.codePostal}")
    private String codePostal;

    @Size(max = 100, message = "{validation.adresse.localite}")
    private String localite;

    @Size(max = 60, message = "{validation.adresse.pays}")
    private String pays;

    private Langue langue = Langue.fr;

    public static ProfilForm de(Utilisateur membre) {
        ProfilForm formulaire = new ProfilForm();
        formulaire.prenom = membre.getPrenom();
        formulaire.nom = membre.getNom();
        formulaire.telephone = membre.getTelephone();
        formulaire.rue = membre.getRue();
        formulaire.numeroRue = membre.getNumeroRue();
        formulaire.codePostal = membre.getCodePostal();
        formulaire.localite = membre.getLocalite();
        formulaire.pays = membre.getPays();
        formulaire.langue = membre.getLangue();
        return formulaire;
    }

    /**
     * Les trois champs que la facture exige sont fournis <b>ensemble</b>.
     *
     * <p>Une adresse partielle n a pas de sens : {@code adresseLisible()} de la
     * facture rend {@code null} des qu il en manque un, et le document sortirait sans
     * adresse — mention pourtant obligatoire (AR n°1, art. 5). Autant le dire au
     * moment de la saisie.</p>
     *
     * <p>Le numero de rue n en fait pas partie : la facture s en passe, et certaines
     * adresses belges n en ont pas.</p>
     */
    public boolean adresseCoherente() {
        return adresseVide() || adresseComplete();
    }

    public boolean adresseVide() {
        return vide(rue) && vide(codePostal) && vide(localite);
    }

    public boolean adresseComplete() {
        return !vide(rue) && !vide(codePostal) && !vide(localite);
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getRue() { return rue; }
    public void setRue(String rue) { this.rue = rue; }
    public String getNumeroRue() { return numeroRue; }
    public void setNumeroRue(String numeroRue) { this.numeroRue = numeroRue; }
    public String getCodePostal() { return codePostal; }
    public void setCodePostal(String codePostal) { this.codePostal = codePostal; }
    public String getLocalite() { return localite; }
    public void setLocalite(String localite) { this.localite = localite; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public Langue getLangue() { return langue; }
    public void setLangue(Langue langue) { this.langue = langue; }
}
