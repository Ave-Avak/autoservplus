package be.autoservplus.common.debit;

import be.autoservplus.identite.service.GardeConnexion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applique {@link Plafonne} avant le contrôleur.
 *
 * <p>Un intercepteur de Spring MVC et non un filtre de la chaîne de sécurité : il voit la
 * méthode retenue, donc son annotation, et l'identité déjà établie. Un refus d'accès passe
 * avant lui et ne consomme rien.</p>
 *
 * <p>L'adresse du client est {@code getRemoteAddr}, que la vanne RemoteIp de Tomcat rend
 * juste derrière le proxy. Une IPv6 est comptée par /64, comme à la connexion.</p>
 */
class IntercepteurPlafonds implements HandlerInterceptor {

    private final PlafondsRequetes plafonds;

    IntercepteurPlafonds(PlafondsRequetes plafonds) {
        this.plafonds = plafonds;
    }

    @Override
    public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object cible) {
        if (!(cible instanceof HandlerMethod methode)) {
            return true;
        }
        Plafonne plafond = AnnotatedElementUtils.findMergedAnnotation(methode.getMethod(), Plafonne.class);
        if (plafond == null) {
            plafond = AnnotatedElementUtils.findMergedAnnotation(methode.getBeanType(), Plafonne.class);
        }
        if (plafond == null) {
            return true;
        }
        plafonds.consommer(plafond.value(), cle(plafond.value(), requete));
        return true;
    }

    private String cle(String surface, HttpServletRequest requete) {
        if (plafonds.par(surface) == ProprietesPlafonds.Par.COMPTE) {
            Authentication identite = SecurityContextHolder.getContext().getAuthentication();
            if (identite != null && identite.isAuthenticated()
                    && !(identite instanceof AnonymousAuthenticationToken)) {
                return "compte:" + identite.getName();
            }
        }
        return "ip:" + GardeConnexion.cleIp(requete.getRemoteAddr());
    }
}
