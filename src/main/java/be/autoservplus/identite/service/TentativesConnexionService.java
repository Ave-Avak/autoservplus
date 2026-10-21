package be.autoservplus.identite.service;

import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Verrouillage temporaire du compte apres des echecs repetes de connexion.
 *
 * <p>Reagit aux evenements publies par Spring Security. Le compteur d echecs est remis a
 * zero des la premiere connexion reussie. Le verrouillage est temporaire plutot que
 * definitif : un blocage permanent permettrait a un tiers de priver un membre de son
 * compte en saisissant volontairement de mauvais mots de passe.</p>
 *
 * <p><b>Seul le passage en verrouillage est consigne</b> au journal de securite du
 * compte, avec un avis au titulaire. Un mot de passe faux isole ne s ecrit pas : n importe
 * qui peut saisir une adresse connue, et le journal du titulaire se remplirait des gestes
 * d un tiers sans rien lui apprendre.</p>
 */
@Service
public class TentativesConnexionService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(TentativesConnexionService.class);

    private final UtilisateurRepository repository;
    private final JournalSecuriteCompte journal;
    private final ServiceCourriel courriel;
    private final Clock horloge;
    private final int tentativesMax;
    private final Duration dureeVerrouillage;

    public TentativesConnexionService(
            UtilisateurRepository repository,
            JournalSecuriteCompte journal,
            ServiceCourriel courriel,
            Clock horloge,
            @Value("${autoservplus.securite.tentatives-max:5}") int tentativesMax,
            @Value("${autoservplus.securite.duree-verrouillage-minutes:15}") long minutes) {
        this.repository = repository;
        this.journal = journal;
        this.courriel = courriel;
        this.horloge = horloge;
        this.tentativesMax = tentativesMax;
        this.dureeVerrouillage = Duration.ofMinutes(minutes);
    }

    @EventListener
    @Transactional
    public void surEchec(AuthenticationFailureBadCredentialsEvent evenement) {
        String email = String.valueOf(evenement.getAuthentication().getName());
        repository.findByEmailIgnoreCase(email).ifPresent(membre -> {
            Instant maintenant = Instant.now(horloge);
            boolean dejaVerrouille = membre.estVerrouille(maintenant);
            Instant finVerrouillage = maintenant.plus(dureeVerrouillage);
            membre.enregistrerEchecConnexion(tentativesMax, finVerrouillage);
            if (!dejaVerrouille && membre.estVerrouille(maintenant)) {
                journal.consigner(membre, TypeEvenementSecurite.CONNEXION_VERROUILLAGE, null);
                JOURNAL.warn("Compte verrouille apres {} echecs : {}", tentativesMax, email);
                notifierVerrouillage(membre, finVerrouillage);
            }
        });
    }

    /**
     * Tentative refusee parce que le compte est deja verrouille. Spring la rejette avant
     * de lire le mot de passe : sans cette ligne, elle ne laissait aucune trace.
     */
    @EventListener
    public void surTentativeVerrouillee(AuthenticationFailureLockedEvent evenement) {
        JOURNAL.info("Connexion refusee, compte verrouille : {}",
                evenement.getAuthentication().getName());
    }

    /**
     * Connexion ABOUTIE : appelee a la fin de la derniere etape, et non sur
     * {@code AuthenticationSuccessEvent}, que Spring Security publie des que le mot de
     * passe est verifie. Reagir a cet evenement remettait a zero les compteurs et datait
     * la connexion avant le second facteur — un mot de passe juste suivi de codes faux
     * aurait ete enregistre comme une connexion reussie.
     */
    @Transactional
    public void connexionAboutie(String email) {
        repository.findByEmailIgnoreCase(email)
                .ifPresent(membre -> membre.enregistrerConnexionReussie(Instant.now(horloge)));
    }

    /**
     * Le verrouillage est deja ecrit : une panne du fournisseur de courriel ne doit pas
     * l annuler. Meme arbitrage que {@code SecondFacteurService.notifierVerrouillage}.
     */
    private void notifierVerrouillage(Utilisateur compte, Instant jusqua) {
        try {
            courriel.envoyerAvisVerrouillageConnexion(compte, jusqua);
        } catch (RuntimeException echec) {
            JOURNAL.warn("Avis de verrouillage non envoye pour le compte {} : {}",
                    compte.getReference(), echec.getMessage());
        }
    }
}