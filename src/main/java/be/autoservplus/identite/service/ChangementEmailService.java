package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsChangementEmailCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Changement d adresse de courriel avec verification prealable (CdC 5.2.2).
 *
 * <p><b>L adresse ne bascule qu une fois la nouvelle prouvee.</b> L alternative —
 * appliquer tout de suite, verifier ensuite — enferme hors de son compte tout membre
 * qui se trompe d une lettre : l adresse en base ne lui appartient plus, la
 * reinitialisation de mot de passe part vers une boite qui n existe pas, et plus rien
 * ne le rattache a sa ligne. L erreur de saisie n est pas un cas rare, c est le cas
 * ordinaire.</p>
 *
 * <p><b>Trois gardes, de natures differentes.</b> Le mot de passe etablit QUI agit —
 * une session volee ne suffit pas a deplacer l adresse, qui est la cle de toutes les
 * recuperations ulterieures. L exigence d une adresse d origine deja verifiee empeche
 * d enchainer les adresses sans jamais en prouver aucune. Le lien envoye a la nouvelle
 * adresse etablit enfin qu elle est joignable et controlee.</p>
 *
 * <p><b>Neutralite.</b> Le membre qui demande ne doit jamais apprendre si l adresse
 * visee appartient deja a quelqu un : la reponse serait un oracle d existence de
 * compte, sur un formulaire accessible a tout inscrit. Le service se comporte donc a
 * l identique — meme ecran, meme avis a l ancienne adresse — et se contente de ne pas
 * envoyer le lien. Meme parti que {@link MotDePasseService#demanderReinitialisation}
 * et {@link InscriptionService#demanderRenvoiVerification}.</p>
 */
@Service
public class ChangementEmailService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ChangementEmailService.class);

    /**
     * Validite volontairement courte. Ce jeton ne donne pas acces au compte — il
     * deplace seulement l adresse — mais une demande qui traine est une prise ouverte :
     * un lien recu il y a des semaines s applique encore alors que le membre a oublie
     * l avoir demande, et le simple fait de le suivre par curiosite ferait basculer le
     * compte.
     */
    public static final Duration VALIDITE_JETON = Duration.ofHours(2);

    private final UtilisateurRepository repository;
    private final ServiceCourriel courriel;
    private final PasswordEncoder encodeur;
    private final LimiteurDemandesCourriel limiteur;
    private final Clock horloge;
    private final SecureRandom aleatoire = new SecureRandom();

    public ChangementEmailService(UtilisateurRepository repository, ServiceCourriel courriel,
                                  PasswordEncoder encodeur,
                                  LimiteurDemandesCourriel limiteur, Clock horloge) {
        this.repository = repository;
        this.courriel = courriel;
        this.encodeur = encodeur;
        this.limiteur = limiteur;
        this.horloge = horloge;
    }

    /**
     * Enregistre une demande de changement et envoie les deux courriels.
     *
     * <p>Ne rend rien, et c est le point : un resultat exploitable par l appelant
     * finirait par etre affiche, donc par distinguer les cas que la neutralite doit
     * confondre.</p>
     *
     * <p>Les messages portes par l exception sont des phrases francaises, conformement
     * au contrat de {@link RegleMetierException} ; c est le CODE que le controleur
     * traduit, comme {@code RdvController} le fait deja.</p>
     *
     * @throws RegleMetierException si le mot de passe est faux, l adresse d origine non
     *                              verifiee, l adresse identique a l actuelle, ou le
     *                              plafond de demandes atteint — tous des refus qui ne
     *                              parlent que du compte de l appelant
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void demander(String emailCourant, String nouvelleAdresse, String motDePasse, String ip) {
        Utilisateur membre = repository.findByEmailIgnoreCase(normaliser(emailCourant))
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", emailCourant));

        if (motDePasse == null || motDePasse.isEmpty()
                || !encodeur.matches(motDePasse, membre.getMotDePasseHache())) {
            JOURNAL.warn("Changement d adresse refuse : re-authentification echouee pour {}",
                    membre.getReference());
            throw new RegleMetierException("RM-32",
                    "Mot de passe incorrect.");
        }

        // Une adresse d origine non prouvee ne permet pas de conclure que le demandeur
        // controle le compte : il pourrait s etre inscrit avec l adresse d autrui et
        // s en detacher avant que le titulaire ne s en apercoive.
        if (!membre.isEmailVerifie()) {
            throw new RegleMetierException("RM-33",
                    "Verifiez d abord votre adresse actuelle.");
        }

        String cible = normaliser(nouvelleAdresse);
        if (cible.equals(normaliser(membre.getEmail()))) {
            throw new RegleMetierException("RM-34",
                    "Cette adresse est deja la votre.");
        }

        // Le plafond precede toute lecture liee a la cible, comme pour les formulaires
        // publics : un decompte qui dependrait de l existence de l adresse rendrait le
        // refus bavard.
        if (!limiteur.autoriser(cible, ip)) {
            throw new RegleMetierException("RM-35",
                    "Trop de demandes. Reessayez dans quelques minutes.");
        }

        String prenom = membre.getPrenom();
        String ancienne = membre.getEmail();

        if (adresseIndisponible(cible)) {
            // Rien vers la cible : l aviser permettrait a tout inscrit d expedier un
            // message a n importe quel membre en devinant son adresse, plafond ou non.
            // Le journal garde le fait pour l exploitant.
            JOURNAL.info("Changement d adresse sans effet pour {} : adresse cible indisponible.",
                    membre.getReference());
        } else {
            String jeton = genererJeton();
            membre.demanderChangementEmail(cible, jeton, Instant.now(horloge).plus(VALIDITE_JETON));
            courriel.envoyerConfirmationNouvelleAdresse(
                    new DetailsChangementEmailCourriel(cible, prenom, ancienne, cible),
                    "/changement-adresse/confirmation?jeton=" + jeton);
        }

        // L avis part dans les DEUX cas, et des la demande. Dans les deux cas, parce
        // que son absence trahirait l indisponibilite de la cible. Des la demande,
        // parce qu averti seulement a la bascule le titulaire legitime n aurait plus
        // aucun moyen de reprendre la main.
        courriel.envoyerAvisChangementAdresse(
                new DetailsChangementEmailCourriel(ancienne, prenom, ancienne, cible));
    }

    /**
     * Applique un changement dont le lien vient d etre suivi.
     *
     * <p>Contrairement a {@link #demander}, ce point d entree NOMME la situation : son
     * appelant est le porteur d un jeton, qui sait deja de quel compte il s agit
     * puisqu il a recu le lien. Taire l expiration ne protegerait personne et
     * laisserait le membre sans explication.</p>
     *
     * @return l ancienne adresse, pour que l appelant puisse la journaliser
     * @throws RessourceIntrouvableException si aucune demande ne porte ce jeton
     * @throws RegleMetierException          si le lien a expire, ou si l adresse a ete
     *                                       prise entre la demande et la confirmation
     */
    @Transactional
    public String confirmer(String jeton) {
        Utilisateur membre = repository.findByJetonChangementEmail(jeton)
                .orElseThrow(() -> new RessourceIntrouvableException("Changement d adresse", jeton));

        if (membre.jetonChangementExpire(Instant.now(horloge))) {
            membre.annulerChangementEmail();
            throw new RegleMetierException("RM-36",
                    "Ce lien a expire. Refaites la demande depuis votre profil.");
        }

        // Revalidation obligatoire : l index partiel reserve l adresse contre une autre
        // DEMANDE, pas contre une INSCRIPTION intervenue depuis. Sans ce controle, la
        // bascule echouerait sur uq_utilisateur_email au flush — c est-a-dire par une
        // erreur technique, au pire moment et sans message exploitable.
        if (repository.existsByEmailIgnoreCase(membre.getEmailEnAttente())) {
            membre.annulerChangementEmail();
            throw new RegleMetierException("RM-37",
                    "Cette adresse n est plus disponible.");
        }

        String ancienne = membre.appliquerChangementEmail();
        JOURNAL.info("Adresse de courriel changee pour le compte {}", membre.getReference());
        return ancienne;
    }

    /** Une adresse deja portee par un compte, ou deja reservee par une demande. */
    private boolean adresseIndisponible(String adresse) {
        return repository.existsByEmailIgnoreCase(adresse)
                || repository.existsByEmailEnAttenteIgnoreCase(adresse);
    }

    private String genererJeton() {
        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }

    private static String normaliser(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
