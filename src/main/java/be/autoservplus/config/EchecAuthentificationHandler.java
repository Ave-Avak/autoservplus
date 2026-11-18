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
 * <p>Chaque echec est compte pour l IP de la requete ({@link GardeConnexion}). Le refus
 * prononce avant l authentification, plafond atteint, reprend la meme reponse sans
 * compter.</p>
 */
@Component
public class EchecAuthentificationHandler extends SimpleUrlAuthenticationFailureHandler {

    public static final String DESTINATION = "/connexion?erreur";

    private final GardeConnexion garde;

    public EchecAuthentificationHandler(GardeConnexion garde) {
        super(DESTINATION);
        this.garde = garde;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest requete, HttpServletResponse reponse,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        garde.echec(requete.getRemoteAddr());
        super.onAuthenticationFailure(requete, reponse, exception);
    }

    /** Refus prononce avant toute authentification : meme reponse qu un echec, sans le compter. */
    public void refuserSansCompter(HttpServletRequest requete, HttpServletResponse reponse)
            throws IOException, ServletException {
        super.onAuthenticationFailure(requete, reponse,
                new BadCredentialsException("Connexion refusee avant authentification."));
    }
}
