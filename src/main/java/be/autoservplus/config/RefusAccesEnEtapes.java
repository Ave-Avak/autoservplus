package be.autoservplus.config;

import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.identite.web.EtapeConnexion;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

import java.io.IOException;

/**
 * Un refus d acces pendant une connexion partielle renvoie a l etape en cours.
 *
 * <p>Le refus lui-meme est deja acquis — la connexion partielle n a aucune autorite, et
 * {@code AuthentificationAboutie} l ecarte des routes authentifiees. Ce gestionnaire ne
 * decide rien, il oriente : un 403 brut laisserait l utilisateur devant une page
 * d erreur, sans savoir qu il lui reste un code a saisir. Tout autre refus suit le
 * comportement par defaut.</p>
 */
public class RefusAccesEnEtapes implements AccessDeniedHandler {

    private final AccessDeniedHandler parDefaut = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse,
                       AccessDeniedException refus) throws IOException, ServletException {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof AuthentificationPartielle) {
            reponse.sendRedirect(requete.getContextPath() + EtapeConnexion.PREFIXE);
            return;
        }
        parDefaut.handle(requete, reponse, refus);
    }
}
