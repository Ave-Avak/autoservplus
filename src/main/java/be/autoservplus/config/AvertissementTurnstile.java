package be.autoservplus.config;

import be.autoservplus.identite.service.VerificateurTurnstile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Avertit au démarrage quand Turnstile est désactivé hors des profils demo et test.
 *
 * <p>Sans clé secrète, l'application démarre et fonctionne : inscription, mot de passe
 * oublié et connexion gardent leurs autres protections. Mais la connexion n'exige plus
 * aucune vérification humaine après trois échecs, et rien ne le dirait à l'exploitant.
 * Refuser le démarrage serait excessif, une panne de Cloudflare étant déjà tolérée.</p>
 */
@Component
public class AvertissementTurnstile {

    private static final Logger JOURNAL = LoggerFactory.getLogger(AvertissementTurnstile.class);

    private final Optional<VerificateurTurnstile> turnstile;
    private final Environment environnement;

    public AvertissementTurnstile(Optional<VerificateurTurnstile> turnstile,
                                  Environment environnement) {
        this.turnstile = turnstile;
        this.environnement = environnement;
    }

    /** Vrai si l'avertissement est dû : aucun vérificateur, et ni demo ni test actif. */
    boolean avertissementDu() {
        return turnstile.isEmpty() && !environnement.acceptsProfiles(Profiles.of("demo | test"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        if (avertissementDu()) {
            JOURNAL.warn("Turnstile desactive (TURNSTILE_CLE_SECRETE absente) : la connexion"
                    + " n exige aucune verification humaine apres trois echecs, et"
                    + " l inscription comme le mot de passe oublie se passent du defi.");
        }
    }
}
