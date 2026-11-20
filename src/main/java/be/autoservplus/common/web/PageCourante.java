package be.autoservplus.common.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Adresse de la page demandée par le visiteur, celle vers laquelle le sélecteur de langue
 * et le bandeau des cookies le ramènent.
 *
 * <p>Quand le conteneur rend une page d'erreur, la requête est redistribuée vers
 * {@code /error} et {@code getRequestURI()} rend ce chemin. Un lien de langue vers
 * {@code /error?lang=nl} ouvrirait une page d'erreur sans statut. Le chemin d'origine est
 * dans l'attribut {@link RequestDispatcher#ERROR_REQUEST_URI}. Les paramètres d'origine,
 * eux, ne sont pas repris : une page d'erreur ne renvoie pas une chaîne de requête qu'elle
 * n'a pas su traiter, et qui peut porter un jeton.</p>
 */
public final class PageCourante {

    private PageCourante() {
    }

    public static String chemin(HttpServletRequest requete) {
        if (requete.getDispatcherType() == DispatcherType.ERROR
                && requete.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String origine) {
            return origine;
        }
        return requete.getRequestURI();
    }

    /** Chaîne de requête de la page, nulle sur une page d'erreur. */
    public static String parametres(HttpServletRequest requete) {
        return requete.getDispatcherType() == DispatcherType.ERROR ? null : requete.getQueryString();
    }
}
