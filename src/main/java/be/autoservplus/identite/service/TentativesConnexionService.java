package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
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
 */
@Service
public class TentativesConnexionService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(TentativesConnexionService.class);

    private final UtilisateurRepository repository;
    private final Clock horloge;
    private final int tentativesMax;
    private final Duration dureeVerrouillage;

    public TentativesConnexionService(
            UtilisateurRepository repository,
            Clock horloge,
            @Value("${autoservplus.securite.tentatives-max:5}") int tentativesMax,
            @Value("${autoservplus.securite.duree-verrouillage-minutes:15}") long minutes) {
        this.repository = repository;
        this.horloge = horloge;
        this.tentativesMax = tentativesMax;
        this.dureeVerrouillage = Duration.ofMinutes(minutes);
    }

    @EventListener
    @Transactional
    public void surEchec(AuthenticationFailureBadCredentialsEvent evenement) {
        String email = String.valueOf(evenement.getAuthentication().getName());
        repository.findByEmailIgnoreCase(email).ifPresent(membre -> {
            Instant finVerrouillage = Instant.now(horloge).plus(dureeVerrouillage);
            membre.enregistrerEchecConnexion(tentativesMax, finVerrouillage);
            if (membre.estVerrouille(Instant.now(horloge))) {
                JOURNAL.warn("Compte verrouille apres {} echecs : {}", tentativesMax, email);
            }
        });
    }

    @EventListener
    @Transactional
    public void surSucces(AuthenticationSuccessEvent evenement) {
        String email = evenement.getAuthentication().getName();
        repository.findByEmailIgnoreCase(email)
                .ifPresent(membre -> membre.enregistrerConnexionReussie(Instant.now(horloge)));
    }
}