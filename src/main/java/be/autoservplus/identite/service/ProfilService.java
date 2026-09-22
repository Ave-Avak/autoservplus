package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.repository.FactureRepository;
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
    private final FactureRepository factures;

    public ProfilService(UtilisateurRepository utilisateurs, FactureRepository factures) {
        this.utilisateurs = utilisateurs;
        this.factures = factures;
    }

    public Utilisateur profilDe(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
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
        boolean adresseDevientVide = rue == null || rue.isBlank()
                || codePostal == null || codePostal.isBlank()
                || localite == null || localite.isBlank();

        if (adresseDevientVide && membre.adressePostaleComplete() && aDesFactures(email)) {
            // Constructeur SANS code : aucune regle du CdC ne porte ce refus, et
            // fabriquer un « RM-31 » polluerait une tracabilite qui vaut precisement
            // parce qu elle renvoie a une exigence reelle. Le message se suffit donc a
            // lui-meme, comme le Javadoc de l exception l exige.
            throw new RegleMetierException(
                    "Renseignez votre adresse : elle figure sur vos factures.");
        }

        membre.modifierProfil(prenom, nom, telephone, rue, numeroRue, codePostal,
                localite, pays, langue);
        return membre;
    }

    /**
     * Le controle ne porte pas sur les PDF deja archives — ceux-la sont immuables et
     * gardent l adresse qu ils portaient. Il porte sur les factures dont le document
     * <b>n a pas encore ete genere</b> : {@code PdfFactureService} lit l entite vive au
     * moment de le produire, si bien qu une adresse effacee aujourd hui sortirait sur
     * une facture emise il y a des mois — sans sa mention obligatoire.
     */
    private boolean aDesFactures(String email) {
        return factures.countByMembreEmailIgnoreCase(email) > 0;
    }
}
