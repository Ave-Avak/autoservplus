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
 * sens de l exemption de l article 129 de la loi du 13 juin 2005 — ce qui rouvrirait
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
 * ({@link #setLocale}) comme ce dont il herite faute de choix
 * ({@link #determineDefaultLocale}). Filtrer dans l intercepteur de changement
 * aurait laisse passer tout autre appelant.</p>
 */
public class ResolveurLangueSession extends SessionLocaleResolver {

    /**
     * Langue heritee quand aucun choix n a ete exprime dans la session.
     *
     * <p>L en-tete {@code Accept-Language} du navigateur reste consulte — c est le
     * comportement qui existait avant F6, et le supprimer aurait servi du francais a
     * un visiteur neerlandophone qui n a rien demande. Il est simplement <b>ramene a
     * l ensemble admis</b> : une langue inconnue donne du francais, pas un document
     * qui s annonce dans une langue qu il ne parle pas.</p>
     */
    @Override
    protected Locale determineDefaultLocale(HttpServletRequest requete) {
        return LanguesSupportees.plusProche(requete.getLocale());
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
