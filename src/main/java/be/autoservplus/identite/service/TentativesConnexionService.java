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
 * Verrouillage temporaire du compte après des échecs repetes de connexion.
 *
 * <p>Réagit aux événements publiés par Spring Security. Le seuil vaut sur une fenêtre
 * ouverte par le premier échec (voir {@link Utilisateur#enregistrerEchecConnexion}), et
 * le compteur est remis à zéro dès la connexion réussie. Le verrouillage est temporaire
 * plutôt que définitif : un blocage permanent permettrait à un tiers de priver un membre de son
 * compte en saisissant volontairement de mauvais mots de passe.</p>
 *
 * <p><b>Seul le passage en verrouillage est consigné</b> au journal de sécurité du
 * compte, avec un avis au titulaire. Un mot de passe faux isolé ne s'écrit pas : n'importe
 * qui peut saisir une adresse connue, et le journal du titulaire se remplirait des gestes
 * d'un tiers sans rien lui apprendre.</p>
 */
@Service
public class TentativesConnexionService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(TentativesConnexionService.class);

    private final UtilisateurRepository repository;
    private final JournalSecuriteCompte journal;
    private final ServiceCourriel courriel;
    private final GardeConnexion garde;
    private final Clock horloge;
    private final int tentativesMax;
    private final Duration fenetre;
    private final Duration dureeVerrouillage;

    public TentativesConnexionService(
            UtilisateurRepository repository,
            JournalSecuriteCompte journal,
            ServiceCourriel courriel,
            GardeConnexion garde,
            Clock horloge,
            @Value("${autoservplus.securite.tentatives-max:5}") int tentativesMax,
            @Value("${autoservplus.securite.fenetre-echecs-minutes:15}") long fenetreMinutes,
            @Value("${autoservplus.securite.duree-verrouillage-minutes:15}") long minutes) {
        this.repository = repository;
        this.journal = journal;
        this.courriel = courriel;
        this.garde = garde;
        this.horloge = horloge;
        this.tentativesMax = tentativesMax;
        this.fenetre = Duration.ofMinutes(fenetreMinutes);
        this.dureeVerrouillage = Duration.ofMinutes(minutes);
    }

    @EventListener
    @Transactional
    public void surEchec(AuthenticationFailureBadCredentialsEvent evenement) {
        String email = AdresseCourriel.normaliser(String.valueOf(evenement.getAuthentication().getName()));
        repository.findByEmail(email).ifPresent(membre -> {
            Instant maintenant = Instant.now(horloge);
            if (membre.enregistrerEchecConnexion(tentativesMax, fenetre, dureeVerrouillage,
                    maintenant)) {
                journal.consigner(membre, TypeEvenementSecurite.CONNEXION_VERROUILLAGE, null);
                // La référence du compte suffit au diagnostic : l'adresse est une donnée
                // personnelle, et un journal se lit plus largement que la base (RGPD art. 5.1.c).
                JOURNAL.warn("Compte verrouillé après {} échecs : {}", tentativesMax, membre.getReference());
                notifierVerrouillage(membre, membre.getVerrouilleJusquA());
            }
        });
    }

    /**
     * Tentative refusée parce que le compte est déjà verrouillé. Spring la rejette avant
     * de lire le mot de passe : sans cette ligne, elle ne laissait aucune trace.
     */
    @EventListener
    public void surTentativeVerrouillee(AuthenticationFailureLockedEvent evenement) {
        String email = AdresseCourriel.normaliser(String.valueOf(evenement.getAuthentication().getName()));
        repository.findByEmail(email).ifPresent(membre ->
                JOURNAL.info("Connexion refusée, compte verrouillé : {}", membre.getReference()));
    }

    /**
     * Connexion ABOUTIE : appelée à la fin de la dernière étape, et non sur
     * {@code AuthenticationSuccessEvent}, que Spring Security publie dès que le mot de
     * passe est vérifié. Réagir à cet événement remettait à zéro les compteurs et datait
     * la connexion avant le second facteur : un mot de passe juste suivi de codes faux
     * aurait été enregistré comme une connexion réussie.
     */
    @Transactional
    public void connexionAboutie(String email) {
        garde.succes(email);
        repository.findByEmail(AdresseCourriel.normaliser(email))
                .ifPresent(membre -> membre.enregistrerConnexionReussie(Instant.now(horloge)));
    }

    /**
     * Le verrouillage est déjà écrit : une panne du fournisseur de courriel ne doit pas
     * l'annuler. Même arbitrage que {@code SecondFacteurService.notifierVerrouillage}.
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