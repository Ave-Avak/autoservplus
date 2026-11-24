package be.autoservplus.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

/**
 * Resolveur de langue adosse a la <b>session</b>, et non a un cookie (F6).
 *
 * <p>Le choix du support n est pas technique. Un cookie de langue serait un cookie
 * de plus, et il faudrait alors trancher s il est « strictement necessaire » au
 * sens de l exemption de l article 129 de la loi du 13 juin 2005 : ce qui rouvrirait
 * la question reglee par F25, pour un confort. La session existe deja, elle est
 * couverte par les cookies strictement necessaires deja declares au bandeau, et
 * elle suffit : la preference d un membre connecte est <b>persistee en base</b>
 * dans {@code utilisateur.langue} et rappliquee a chaque nouvelle session par
 * {@link PreferenceLangueInterceptor}. Seul un visiteur anonyme perd son choix en
 * fermant son navigateur, ce qui est le comportement attendu de quelqu un qui n a
 * pas de compte.</p>
 *
 * <p>Cette classe est le <b>point de passage unique</b> ou l ensemble ferme des
 * langues est impose, dans les deux sens : ce que le visiteur demande
 * ({@link #setLocale}) comme ce dont il herite faute de choix (la fonction posée par
 * le constructeur). Filtrer dans l intercepteur de changement aurait laisse passer
 * tout autre appelant.</p>
 */
public class ResolveurLangueSession extends SessionLocaleResolver {

    /**
     * Pose la langue héritée quand aucun choix n'a été exprimé dans la session.
     *
     * <p>L'en-tête {@code Accept-Language} du navigateur reste consulté : le supprimer
     * aurait servi du français à un visiteur néerlandophone qui n'a rien demandé. Il est
     * simplement <b>ramené à l'ensemble admis</b> : une langue inconnue donne du
     * français, pas un document qui s'annonce dans une langue qu'il ne parle pas.</p>
     *
     * <p>Par une fonction et non par la redéfinition de {@code determineDefaultLocale} :
     * depuis Spring 6, {@code resolveLocale} appelle la fonction sans passer par cette
     * méthode, dépréciée. La redéfinition n'était donc jamais appelée, et un navigateur
     * en allemand recevait {@code lang="de"} sur une page en français
     * ({@code ResolveurLangueSessionTest}).</p>
     */
    public ResolveurLangueSession() {
        setDefaultLocaleFunction(requete -> LanguesSupportees.plusProche(requete.getLocale()));
    }

    /**
     * Enregistre le choix apres l avoir ramene a l ensemble admis.
     *
     * <p>{@code LocaleChangeInterceptor} accepte tout etiquetage de langue
     * syntaxiquement valide : {@code ?lang=de} n est pas invalide, il est
     * simplement hors perimetre. Sans ce filtre, il s installerait dans la session
     * et y resterait.</p>
     */
    @Override
    public void setLocale(HttpServletRequest requete, HttpServletResponse reponse, Locale langue) {
        super.setLocale(requete, reponse,
                langue == null ? null : LanguesSupportees.plusProche(langue));
    }
}
