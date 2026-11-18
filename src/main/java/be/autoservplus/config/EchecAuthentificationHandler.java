package be.autoservplus.config;

import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Un seul refus pour toute connexion qui échoue : {@code /connexion?erreur}.
 *
 * <p>Le verrouillage avait sa propre redirection, {@code ?bloque}. Son texte était neutre,
 * mais sa présence ne l'était pas : une adresse inconnue recevait {@code ?erreur} sans fin,
 * une adresse connue passait à {@code ?bloque} à la sixième tentative, ce qui suffisait à
 * énumérer les comptes. Mot de passe faux, compte verrouillé, désactivé ou inexistant
 * répondent désormais de la même façon, et le message unique de la page dit que la
 * connexion peut être bloquée un temps sans dire si elle l'est.</p>
 */
@Component
public class EchecAuthentificationHandler extends SimpleUrlAuthenticationFailureHandler {

    public static final String DESTINATION = "/connexion?erreur";

    public EchecAuthentificationHandler() {
        super(DESTINATION);
    }
}
