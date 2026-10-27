package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Pouvoirs du super-administrateur sur les comptes du back-office (CdC 5.2.3) : creer un
 * administrateur, lui renvoyer son lien, reinitialiser un second facteur.
 *
 * <p><b>Aucun mot de passe ne transite par courriel.</b> Le compte nait avec l empreinte
 * d un secret aleatoire aussitot jete, le patron de l anonymisation : la colonne est
 * NOT NULL et la verification passe par l encodeur, une valeur hors format echouerait
 * sur une exception au lieu d un refus. Le titulaire choisit le sien en suivant un lien
 * a usage unique. Un mot de passe envoye par courriel resterait lisible dans une boite de
 * reception pendant des annees. Un lien consomme ne vaut plus rien.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('SUPER_ADMINISTRATEUR')")
public class ComptesAdministrateursService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ComptesAdministrateursService.class);
    private static final SecureRandom ALEA = new SecureRandom();
    private static final String CHEMIN_DEFINITION = "/compte/definition?jeton=";

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final AuteurCourant auteurCourant;
    private final JournalSecuriteCompte journal;
    private final SecondFacteurService secondFacteur;
    private final RevocationSessions revocation;
    private final ServiceCourriel courriel;
    private final Clock horloge;

    public ComptesAdministrateursService(UtilisateurRepository utilisateurs, PasswordEncoder encodeur,
                                         AuteurCourant auteurCourant, JournalSecuriteCompte journal,
                                         SecondFacteurService secondFacteur,
                                         RevocationSessions revocation, ServiceCourriel courriel,
                                         Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.auteurCourant = auteurCourant;
        this.journal = journal;
        this.secondFacteur = secondFacteur;
        this.revocation = revocation;
        this.courriel = courriel;
        this.horloge = horloge;
    }

    /**
     * Cree un administrateur sans mot de passe utilisable et lui envoie son lien.
     *
     * <p>Le compte reste {@code EN_ATTENTE_VALIDATION}, donc refuse a la connexion, tant
     * que le lien n a pas ete suivi. Le changement impose est arme des maintenant :
     * meme si un mot de passe lui etait pose par un autre chemin, il ne serait pas le
     * sien.</p>
     */
    @Transactional
    public Utilisateur creerAdministrateur(String email, String prenom, String nom, String fonction) {
        Utilisateur auteur = exigerAuteur();
        String adresse = email.strip().toLowerCase(Locale.ROOT);
        if (utilisateurs.existsByEmailIgnoreCase(adresse)) {
            // Le super-administrateur peut apprendre qu une adresse est prise : il n est
            // pas l anonyme que la neutralite des formulaires publics protege.
            throw new RefusTraduit("admin.administrateurs.erreur.adresse-prise",
                    "Un compte utilise deja cette adresse.");
        }
        Utilisateur administrateur = new Utilisateur(adresse, hachageInerte(), nom.strip(),
                prenom.strip(), TypeUtilisateur.ADMINISTRATEUR);
        administrateur.setFonction(fonction == null || fonction.isBlank() ? null : fonction.strip());
        JetonDefinition jeton = JetonDefinition.tirer();
        administrateur.preparerDefinitionMotDePasse(jeton.empreinte(),
                horloge.instant().plus(PremierMotDePasseService.VALIDITE_LIEN));
        utilisateurs.saveAndFlush(administrateur);

        journal.consigner(administrateur, TypeEvenementSecurite.ADMINISTRATEUR_CREE, auteur);
        journal.consigner(administrateur, TypeEvenementSecurite.LIEN_DEFINITION_ENVOYE, auteur);
        courriel.envoyerLienDefinitionMotDePasse(administrateur, CHEMIN_DEFINITION + jeton.clair());
        JOURNAL.info("Administrateur {} cree par {}", administrateur.getReference(),
                auteur.getReference());
        return administrateur;
    }

    /** Renvoie le lien : le nouveau jeton remplace l ancien, qui cesse aussitot de valoir. */
    @Transactional
    public void renvoyerLienDefinition(UUID reference) {
        Utilisateur auteur = exigerAuteur();
        Utilisateur cible = parReference(reference);
        if (!cible.motDePasseADefinir()) {
            throw new RefusTraduit("admin.compte.lien.erreur.deja-defini",
                    "Ce compte a deja defini son mot de passe.");
        }
        JetonDefinition jeton = JetonDefinition.tirer();
        cible.preparerDefinitionMotDePasse(jeton.empreinte(),
                horloge.instant().plus(PremierMotDePasseService.VALIDITE_LIEN));
        journal.consigner(cible, TypeEvenementSecurite.LIEN_DEFINITION_ENVOYE, auteur);
        courriel.envoyerLienDefinitionMotDePasse(cible, CHEMIN_DEFINITION + jeton.clair());
    }

    /**
     * Retire le second facteur d un compte qui a perdu son telephone et ses codes.
     *
     * <p>Toutes les sessions du compte tombent, et le titulaire est averti : une
     * reinitialisation demandee par un usurpateur doit se voir. Pour un compte du
     * back-office, la connexion suivante impose un nouvel enrolement.</p>
     *
     * <p>Le delai de 48 h que la Strategie (§17.4.3) prevoit avant qu une reinitialisation
     * prenne effet n est pas implemente : voir le registre.</p>
     */
    @Transactional
    public void reinitialiserSecondFacteur(UUID reference) {
        Utilisateur auteur = exigerAuteur();
        Utilisateur cible = parReference(reference);
        if (auteur.getId().equals(cible.getId())) {
            // Son propre second facteur se retire depuis son profil, apres mot de passe
            // et code. Le permettre ici en ferait un raccourci sans ces deux gardes.
            throw new RefusTraduit("admin.compte.mfa.erreur.soi-meme",
                    "Vous ne pouvez pas reinitialiser votre propre second facteur.");
        }
        if (!cible.secondFacteurActif() && !cible.enrolementEnCours()) {
            throw new RefusTraduit("admin.compte.mfa.erreur.inactif",
                    "Ce compte n a pas de second facteur.");
        }
        secondFacteur.retirer(cible);
        journal.consigner(cible, TypeEvenementSecurite.MFA_REINITIALISE, auteur);
        revocation.revoquer(cible.getEmail());
        courriel.envoyerAvisReinitialisationSecondFacteur(cible);
        JOURNAL.info("Second facteur du compte {} reinitialise par {}", cible.getReference(),
                auteur.getReference());
    }

    private Utilisateur parReference(UUID reference) {
        return utilisateurs.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Compte", reference.toString()));
    }

    private Utilisateur exigerAuteur() {
        Utilisateur auteur = auteurCourant.resoudre();
        if (auteur == null) {
            throw new IllegalStateException(
                    "Aucun auteur authentifie : une decision sur un compte doit etre imputable.");
        }
        return auteur;
    }

    private String hachageInerte() {
        byte[] secret = new byte[32];
        ALEA.nextBytes(secret);
        return encodeur.encode(Base64.getEncoder().encodeToString(secret));
    }
}
