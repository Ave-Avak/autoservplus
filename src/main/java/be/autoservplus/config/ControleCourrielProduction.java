package be.autoservplus.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exige un relais SMTP au démarrage, sous le profil {@code prod} seulement.
 *
 * <p>Même parti que {@link ControleIdentiteGarage} : le contrôle porte sur le profil et
 * non sur la présence des variables, qu'une production ne doit pas pouvoir omettre. Sans
 * relais, les courriels partiraient au journal, liens à jeton compris.</p>
 */
@Component
@Profile("prod")
class ControleCourrielProduction {

    ControleCourrielProduction(ProprietesCourriel courriel) {
        courriel.verifierPourProduction();
    }
}
