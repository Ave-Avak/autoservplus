package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import be.autoservplus.communication.service.LiensCourriel;
import be.autoservplus.communication.service.ServiceCourriel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Inscription d un membre et verification de son adresse de courriel.
 *
 * <p>Couvre la fonctionnalite F1 du cahier des charges. Le compte reste au statut
 * EN_ATTENTE_VALIDATION tant que l adresse n a pas ete confirmee : cela evite qu une
 * inscription faite avec l adresse d autrui donne acces au service.</p>
 */
@Service
@Transactional(readOnly = true)
public class InscriptionService {

    /** Duree de validite du jeton de verification. */
    public static final Duration VALIDITE_JETON = Duration.ofHours(24);

    /** Clé du refus d'une inscription sans acceptation des CGV, affiché sous la case. */
    public static final String CLE_CGV_OBLIGATOIRES = "inscription.cgv.obligatoire";

    private static final Logger JOURNAL = LoggerFactory.getLogger(InscriptionService.class);

    private final UtilisateurRepository repository;
    private final PolitiqueMotDePasse politique;
    private final PasswordEncoder encodeurMotDePasse;
    private final Clock horloge;
    private final ServiceCourriel courriel;
    private final LiensCourriel liens;
    private final MotDePasseService motsDePasse;
    private final LimiteurDemandesCourriel limiteur;
    private final ConsentementRepository consentements;
    private final VersionsDocumentsService versionsDocuments;

    public InscriptionService(UtilisateurRepository repository,
                              PasswordEncoder encodeurMotDePasse,
                              Clock horloge,
                              ServiceCourriel courriel,
                              PolitiqueMotDePasse politique,
                              LiensCourriel liens,
                              MotDePasseService motsDePasse,
                              LimiteurDemandesCourriel limiteur,
                              ConsentementRepository consentements,
                              VersionsDocumentsService versionsDocuments) {
        this.repository = repository;
        this.consentements = consentements;
        this.versionsDocuments = versionsDocuments;
        this.motsDePasse = motsDePasse;
        this.limiteur = limiteur;
        this.liens = liens;
        this.politique = politique;
        this.encodeurMotDePasse = encodeurMotDePasse;
        this.horloge = horloge;
        this.courriel = courriel;
    }

    /**
     * Inscrit un nouveau membre et génère son jeton de vérification, ou, si l'adresse a
     * déjà un compte, prévient son titulaire sans rien créer (RM-01).
     *
     * <p><b>Aucune réponse ne dit si l'adresse a un compte</b> (F1). Le refus affiché sous
     * le champ en faisait un oracle, et le chemin de l'adresse connue, qui sautait la
     * politique, Have I Been Pwned et BCrypt, se distinguait aussi par sa durée. Les deux
     * chemins passent désormais par la politique, appel à Have I Been Pwned compris, puis
     * par un hachage BCrypt, jeté pour l'adresse connue. Seuls l'enregistrement et le
     * courriel diffèrent, et les courriels partent hors de la requête.</p>
     *
     * <p>L'acceptation des CGV est exigée ici et non seulement par le formulaire (F1) : un
     * envoi forgé sans la case est refusé, avant toute recherche de l'adresse, donc de la
     * même façon qu'elle ait un compte ou non. La preuve porte la version en vigueur et
     * n'est rattachée à aucune commande. Elle ne remplace pas l'acceptation demandée à
     * chaque commande, seule à porter sur la version en vigueur le jour de la vente. Une
     * adresse qui a déjà un compte n'en reçoit aucune : rien n'est créé.</p>
     *
     * <p>Le courriel de vérification part après la validation de la transaction. Un
     * échec de remise n'annule donc pas l'inscription : le compte existe, et le renvoi de
     * vérification comme la demande d'un nouveau mot de passe donnent un autre lien.</p>
     *
     * @param cgvAcceptees état de la case des CGV tel que reçu, jamais supposé
     * @param ip adresse de la requête, pour le plafond des courriels au titulaire et la
     *           preuve d'acceptation des CGV
     * @return le compte créé, ou rien si l'adresse en avait déjà un
     * @throws RefusTraduit si les CGV ne sont pas acceptées ou si le mot de passe ne
     *                      respecte pas {@link PolitiqueMotDePasse}, quelle que soit l'adresse
     */
    @Transactional
    public Optional<Utilisateur> inscrire(String email, String motDePasseClair,
                                          String nom, String prenom, Langue langue,
                                          boolean cgvAcceptees, String ip) {

        if (!cgvAcceptees) {
            throw new RefusTraduit(CLE_CGV_OBLIGATOIRES,
                    "Vous devez accepter les conditions générales de vente pour créer un compte.");
        }
        String emailNormalise = normaliser(email);
        politique.exiger(motDePasseClair);
        String empreinte = encodeurMotDePasse.encode(motDePasseClair);

        Optional<Utilisateur> existant = repository.findByEmail(emailNormalise);
        if (existant.isPresent()) {
            prevenirTitulaire(existant.get(), ip);
            return Optional.empty();
        }

        Utilisateur membre = new Utilisateur(
                emailNormalise,
                empreinte,
                nom.trim(),
                prenom.trim(),
                TypeUtilisateur.MEMBRE);

        membre.setLangue(langue == null ? Langue.fr : langue);
        JetonLien jeton = armerVerification(membre);

        Utilisateur enregistre = enregistrer(membre);
        consentements.save(Consentement.acceptation(enregistre, TypeDocumentConsentement.CGV,
                versionsDocuments.versionCourante(TypeDocumentVersionne.CGV), ip,
                Instant.now(horloge)));
        courriel.envoyerVerificationAdresse(enregistre, lienVerification(jeton));
        return Optional.of(enregistre);
    }

    /**
     * Courriel au titulaire d'une adresse qui a déjà un compte, selon son statut. Le
     * limiteur en borne la fréquence : refusé, rien ne part, et la page reste la même.
     * Seul le budget de l'adresse est consommé ici. Celui de l'IP l'a été avant la recherche
     * du compte, pour toute adresse.
     */
    private void prevenirTitulaire(Utilisateur titulaire, String ip) {
        if (!limiteur.autoriserAdresse(titulaire.getEmail())) {
            return;
        }
        switch (titulaire.getStatut()) {
            // Avertissement sans jeton : la tentative ne doit rien ouvrir à qui l'a faite.
            case ACTIF, SUSPENDU -> courriel.envoyerAvisTentativeInscription(titulaire);
            // Même courriel que « Mot de passe oublié » : un nouveau lien d'activation,
            // qui remplace le précédent.
            case EN_ATTENTE_VALIDATION -> motsDePasse.envoyerCourrielAdapte(titulaire);
            // Seul un compte anonymisé porte ce statut, et son adresse est un jeton qui ne
            // reçoit rien : l'adresse réelle a été libérée à l'anonymisation.
            case SUPPRIME -> JOURNAL.info("Inscription tentee sur une adresse anonymisee.");
        }
    }

    /**
     * Confirme une adresse de courriel a partir de son jeton et active le compte.
     *
     * @throws RessourceIntrouvableException si le jeton n existe pas
     * @throws RegleMetierException          si le jeton a expire
     */
    @Transactional
    public Utilisateur confirmerAdresse(String jeton) {
        Utilisateur membre = repository.findByJetonVerificationHache(JetonLien.empreinte(jeton))
                // Le jeton ne figure pas dans le message : il ouvrirait le lien a qui lit
                // les journaux d erreur.
                .orElseThrow(() -> new RessourceIntrouvableException("Jeton de verification", "inconnu"));

        if (membre.jetonEstExpire(Instant.now(horloge))) {
            throw new RegleMetierException("RM-03",
                    "Le lien de verification a expire. Demandez un nouvel envoi.");
        }

        membre.confirmerAdresseEmail(Instant.now(horloge));
        return membre;
    }

    /**
     * Traite une demande publique de renvoi du courriel de verification.
     *
     * <p>Ne leve jamais d exception et ne rend aucun resultat : l ecran doit afficher le
     * meme message que l adresse soit inconnue, deja verifiee, ou effectivement
     * renvoyee. Une reponse qui differencierait ces trois cas transformerait ce
     * formulaire public en oracle d existence de compte, permettant d enumerer les
     * membres de la plateforme, exactement ce que
     * {@link MotDePasseService#demanderReinitialisation(String)} evite deja sur le
     * parcours voisin, dont ce point d entree reprend le patron.</p>
     *
     * <p>La neutralite est portee ICI et non dans le controleur : c est une regle du
     * flux, pas une regle de presentation. Un second appelant du service en heriterait
     * donc automatiquement, au lieu d avoir a la reimplementer.</p>
     */
    @Transactional
    public void demanderRenvoiVerification(String email) {
        try {
            renvoyerVerification(email);
        } catch (RessourceIntrouvableException | RegleMetierException refus) {
            // Journalise le motif reel sans jamais le remonter a l appelant : le
            // diagnostic reste disponible a l exploitant, pas au visiteur.
            //
            // Rattraper une RuntimeException sans compromettre la transaction n est
            // possible que parce que l appel est INTERNE : il ne traverse pas le proxy
            // transactionnel, qui seul marque la transaction rollback-only. Passer un
            // jour par une auto-injection pour appeler renvoyerVerification rendrait
            // ce catch inoperant et ferait echouer le commit.
            // La classe du refus seulement : le message d'une ressource introuvable porte
            // l'adresse saisie, celle d'une personne qui n'a peut-être aucun compte.
            JOURNAL.info("Renvoi de vérification sans effet : {}", refus.getClass().getSimpleName());
        }
    }

    /**
     * Regenere un jeton pour un compte dont le lien precedent a expire.
     *
     * <p>Seule l empreinte est en base : le jeton precedent ne peut pas etre relu pour
     * etre renvoye. Un jeton neuf est donc tire, et l ancien cesse de valoir.</p>
     *
     * <p>Leve une exception qui NOMME la situation reelle : reservee a un appelant de
     * confiance. Tout point d entree public doit passer par
     * {@link #demanderRenvoiVerification(String)}.</p>
     *
     * @throws RessourceIntrouvableException si aucun compte ne porte cette adresse
     * @throws RegleMetierException          si l adresse est deja verifiee (RM-04)
     */
    @Transactional
    public Utilisateur renvoyerVerification(String email) {
        Utilisateur membre = repository.findByEmail(normaliser(email))
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));

        if (membre.isEmailVerifie()) {
            throw new RegleMetierException("RM-04",
                    "Cette adresse est deja verifiee.");
        }
        // Un compte suspendu avant sa vérification ne reçoit pas de lien : le suivre ne
        // lèverait plus la suspension, mais rien ne justifie de lui écrire.
        if (membre.getStatut() != StatutUtilisateur.EN_ATTENTE_VALIDATION) {
            throw new RegleMetierException("RM-04",
                    "Ce compte n'attend pas de vérification.");
        }

        JetonLien jeton = armerVerification(membre);
        courriel.envoyerVerificationAdresse(membre, lienVerification(jeton));
        return membre;
    }

    private JetonLien armerVerification(Utilisateur membre) {
        JetonLien jeton = JetonLien.tirer();
        membre.enregistrerJetonVerification(jeton.empreinte(),
                Instant.now(horloge).plus(VALIDITE_JETON));
        return jeton;
    }

    private String lienVerification(JetonLien jeton) {
        return liens.vers("/inscription/verification?jeton=" + jeton.clair());
    }
    private String normaliser(String email) {
        if (email == null || email.isBlank()) {
            throw new RegleMetierException("RM-01", "L adresse de courriel est obligatoire.");
        }
        return AdresseCourriel.normaliser(email);
    }

    /**
     * Écrit le compte tout de suite, pour qu'un refus de la contrainte de casse (V55)
     * devienne une erreur sur le champ et non une erreur au moment de la validation.
     */
    private Utilisateur enregistrer(Utilisateur membre) {
        try {
            return repository.saveAndFlush(membre);
        } catch (DataIntegrityViolationException refus) {
            if (AdresseCourriel.violeLaCasse(refus)) {
                throw new RefusTraduit(AdresseCourriel.CLE_REFUS_CASSE,
                        "Cette adresse contient un caractère qui ne peut pas être enregistré. Vérifiez-la ou utilisez-en une autre.");
            }
            throw refus;
        }
    }
}