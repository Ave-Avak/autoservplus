package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.ModificationCompte;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Coordonnées d'un membre modifiées par l'administrateur, à la demande du membre (A12).
 *
 * <p>Les mêmes champs que le profil, avec les mêmes bornes, plus le motif qui note la
 * demande. Ni l'adresse de courriel ni le mot de passe : la première suit un parcours
 * de confirmation propre au titulaire, le second n'est connu que de lui.</p>
 */
public class ModificationCompteForm extends ProfilForm {

    @NotBlank(message = "{admin.compte.modification.motif.obligatoire}")
    @Size(max = ModificationCompte.MOTIF_MAX, message = "{admin.compte.modification.motif.longueur}")
    private String motif;

    public static ModificationCompteForm de(Utilisateur membre) {
        ModificationCompteForm formulaire = new ModificationCompteForm();
        ProfilForm profil = ProfilForm.de(membre);
        formulaire.setPrenom(profil.getPrenom());
        formulaire.setNom(profil.getNom());
        formulaire.setTelephone(profil.getTelephone());
        formulaire.setRue(profil.getRue());
        formulaire.setNumeroRue(profil.getNumeroRue());
        formulaire.setCodePostal(profil.getCodePostal());
        formulaire.setLocalite(profil.getLocalite());
        formulaire.setPays(profil.getPays());
        formulaire.setLangue(profil.getLangue());
        return formulaire;
    }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
