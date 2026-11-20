package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation et modification du profil par le membre lui-meme.
 *
 * <p>L identite vient toujours du contexte de securite : aucune methode ne recoit un
 * identifiant de compte a modifier. Un membre ne peut donc editer que le sien, sans
 * qu aucun controle d appartenance soit a ecrire.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ProfilService {

    private final UtilisateurRepository utilisateurs;
    private final GardeAdresseFactures gardeAdresse;
    private final HistoriqueModificationsCompte historique;

    public ProfilService(UtilisateurRepository utilisateurs, GardeAdresseFactures gardeAdresse,
                         HistoriqueModificationsCompte historique) {
        this.utilisateurs = utilisateurs;
        this.gardeAdresse = gardeAdresse;
        this.historique = historique;
    }

    public Utilisateur profilDe(String email) {
        return utilisateurs.findByEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    /**
     * Enregistre le profil et rend l entite a jour.
     *
     * <p>Le message porte par l exception est une <b>phrase francaise affichable</b>,
     * conformement au contrat de {@link RegleMetierException} : il sert aux journaux
     * et a tout appelant non web. L ecran, lui, rend sa propre cle i18n pour suivre la
     * langue de session (F6).</p>
     *
     * @throws RegleMetierException si le membre vide son adresse postale alors qu une
     *                              facture peut encore etre generee pour lui
     */
    @Transactional
    public Utilisateur enregistrer(String email, String prenom, String nom, String telephone,
                                   String rue, String numeroRue, String codePostal,
                                   String localite, String pays, Langue langue) {
        Utilisateur membre = profilDe(email);
        gardeAdresse.exigerAdresseConservee(membre, rue, codePostal, localite);
        historique.consigner(membre, membre.modifierProfil(prenom, nom, telephone, rue,
                numeroRue, codePostal, localite, pays, langue), membre, null);
        return membre;
    }
}
