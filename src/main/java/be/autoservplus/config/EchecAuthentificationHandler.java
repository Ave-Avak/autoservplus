package be.autoservplus.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Oriente l utilisateur selon la cause du refus d authentification.
 *
 * <p>Le verrouillage donne lieu a un message distinct, car un membre qui ne comprend pas
 * pourquoi il est refuse finit par contourner la securite. La formulation retenue decrit
 * le nombre de tentatives et non l etat d un compte, afin de ne pas confirmer l existence
 * de l adresse saisie.</p>
 */
@Component
public class EchecAuthentificationHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest requete,
                                        HttpServletResponse reponse,
                                        AuthenticationException exception)
            throws IOException, ServletException {

        String destination = (exception instanceof LockedException)
                ? "/connexion?bloque"
                : "/connexion?erreur";

        setDefaultFailureUrl(destination);
        super.onAuthenticationFailure(requete, reponse, exception);
    }
}