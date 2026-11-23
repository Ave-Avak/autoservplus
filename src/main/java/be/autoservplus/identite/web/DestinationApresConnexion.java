package be.autoservplus.identite.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Destination d'une connexion aboutie : la page demandée avant la connexion si elle est
 * une page du site, sinon la page d'arrivée du rôle ({@link PageArrivee}).
 *
 * <p>Un membre qui ouvre le lien de sa commande reçu par courriel est renvoyé à la
 * connexion. Sans cette classe il arrivait ensuite sur « Mon compte » et devait
 * retrouver sa commande à la main.</p>
 *
 * <p><b>Seuls le chemin et les paramètres sont repris</b>, jamais l'adresse complète de
 * la requête sauvegardée : son hôte vient de l'en-tête {@code Host}, que l'appelant
 * choisit. La redirection reste donc sur le site quel que soit cet en-tête. Un chemin qui
 * commence par deux barres ou une barre oblique inverse est écarté : un navigateur le lit
 * comme une adresse d'un autre site.</p>
 *
 * <p>La requête sauvegardée vit dans la session, que les étapes de la connexion (second
 * facteur, changement imposé du mot de passe) conservent : elle est lue une fois, à
 * l'aboutissement, puis retirée. Une page que le rôle n'a pas le droit de voir répond
 * 403 une fois connecté, sans retour à la connexion.</p>
 */
@Component
public class DestinationApresConnexion {

    /** Chemin local, sans caractère de contrôle ni barre oblique inverse. */
    private static final Pattern CHEMIN_LOCAL = Pattern.compile("/[^\\\\\\p{Cntrl}]*");

    /**
     * Adresses où revenir n'a pas de sens : la connexion et ses étapes, la page d'erreur,
     * et ce qui n'est pas une page (interface publique, notifications du prestataire).
     */
    private static final List<String> ECARTEES =
            List.of("/connexion", "/deconnexion", "/error", "/api/", "/webhooks/");

    private final PageArrivee pageArrivee;
    private final RequestCache requetes = new HttpSessionRequestCache();

    public DestinationApresConnexion(PageArrivee pageArrivee) {
        this.pageArrivee = pageArrivee;
    }

    /** Lit la page demandée avant la connexion, la retire de la session, et rend la destination. */
    public String de(HttpServletRequest requete, HttpServletResponse reponse, Authentication complete) {
        SavedRequest sauvegardee = requetes.getRequest(requete, reponse);
        requetes.removeRequest(requete, reponse);
        String demandee = pageDemandee(sauvegardee, requete.getContextPath());
        return demandee != null ? demandee : pageArrivee.de(complete);
    }

    /** Chemin interne de la requête sauvegardée, ou {@code null} si elle ne se reprend pas. */
    static String pageDemandee(SavedRequest sauvegardee, String contexte) {
        if (!(sauvegardee instanceof DefaultSavedRequest demande)
                || !"GET".equalsIgnoreCase(demande.getMethod())
                // Un fragment demandé par HTMX n'est pas une page : affiché seul, il n'a ni
                // en-tête ni style.
                || !demande.getHeaderValues("HX-Request").isEmpty()) {
            return null;
        }
        String chemin = demande.getRequestURI();
        if (chemin == null || !chemin.startsWith(contexte)) {
            return null;
        }
        chemin = chemin.substring(contexte.length());
        String parametres = demande.getQueryString();
        String page = parametres == null || parametres.isBlank() ? chemin : chemin + "?" + parametres;
        if (page.startsWith("//") || !CHEMIN_LOCAL.matcher(page).matches()
                || ECARTEES.stream().anyMatch(chemin::startsWith)
                || estUnFichier(chemin)) {
            return null;
        }
        return page;
    }

    /**
     * Une icône ou une image que le navigateur réclame de lui-même pendant que la page de
     * connexion s'affiche remplace la page demandée dans la session. Aucune page du site
     * ne porte d'extension : un dernier segment qui en a une n'est pas une destination.
     */
    private static boolean estUnFichier(String chemin) {
        return chemin.substring(chemin.lastIndexOf('/') + 1).contains(".");
    }
}
