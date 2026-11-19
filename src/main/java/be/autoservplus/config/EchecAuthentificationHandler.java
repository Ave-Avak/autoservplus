package be.autoservplus.config;

import be.autoservplus.identite.service.GardeConnexion;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Un seul refus pour toute connexion qui échoue : {@code /connexion?erreur}.
 *
 * <p>Le verrouillage avait sa propre redirection, {@code ?bloque}. Son texte était neutre,
 * mais sa présence ne l'était pas : une adresse inconnue recevait {@code ?erreur} sans fin,
 * une adresse connue passait à {@code ?bloque} à la sixième tentative, ce qui suffisait à
 * énumérer les comptes. Mot de passe faux, compte verrouillé, désactivé ou inexistant
 * répondent désormais de la même façon, et le message unique de la page dit que la
 * connexion peut être bloquée un temps sans dire si elle l'est.</p>
 *
 * <p>Chaque échec est compté pour l'IP et pour l'adresse saisie ({@link GardeConnexion}).
 * Le refus prononcé avant l'authentification, plafond atteint ou jeton Turnstile absent,
 * reprend la même réponse sans compter.</p>
 */
@Component
public class EchecAuthentificationHandler extends SimpleUrlAuthenticationFailureHandler {

    public static final String DESTINATION = "/connexion?erreur";

    /**
     * Attribut de session : la page de connexion doit rendre le widget Turnstile. Posé
     * quand la tentative suivante l'exigera, d'après l'IP ou d'après l'adresse saisie,
     * que la page ne connaît plus une fois la redirection faite.
     */
    public static final String VERIFICATION_HUMAINE =
            EchecAuthentificationHandler.class.getName() + ".verificationHumaine";

    private static final String CHAMP_ADRESSE = "email";

    private final GardeConnexion garde;

    public EchecAuthentificationHandler(GardeConnexion garde) {
        super(DESTINATION);
        this.garde = garde;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest requete, HttpServletResponse reponse,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        String adresse = requete.getParameter(CHAMP_ADRESSE);
        garde.echec(adresse, requete.getRemoteAddr());
        if (garde.exigeVerificationHumaine(adresse, requete.getRemoteAddr())) {
            requete.getSession().setAttribute(VERIFICATION_HUMAINE, Boolean.TRUE);
        }
        super.onAuthenticationFailure(requete, reponse, exception);
    }

    /**
     * Refus prononcé avant toute authentification : même réponse qu'un échec, sans le
     * compter.
     *
     * @param verificationHumaine vrai si la page doit rendre le widget Turnstile
     */
    public void refuserSansCompter(HttpServletRequest requete, HttpServletResponse reponse,
                                   boolean verificationHumaine)
            throws IOException, ServletException {
        if (verificationHumaine) {
            requete.getSession().setAttribute(VERIFICATION_HUMAINE, Boolean.TRUE);
        }
        super.onAuthenticationFailure(requete, reponse,
                new BadCredentialsException("Connexion refusee avant authentification."));
    }
}
