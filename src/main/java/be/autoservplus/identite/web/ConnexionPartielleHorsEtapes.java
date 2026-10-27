package be.autoservplus.identite.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hors des etapes de connexion, une connexion PARTIELLE est vue comme anonyme.
 *
 * <p>Le jeton partiel porte le {@code UserDetails} du compte, pour que les etapes
 * sachent qui elles servent. Sur une route publique, il etait donc lu comme une
 * connexion : {@code @AuthenticationPrincipal} le resolvait, et le choix des cookies
 * s enregistrait au nom du compte, les compteurs du panier et des notifications
 * s affichaient, avant que le second facteur ait ete fourni.</p>
 *
 * <p>Place APRES {@code AuthorizationFilter} : les routes protegees ont deja refuse le
 * jeton partiel et renvoye a l etape ({@code RefusAccesEnEtapes}). Ce filtre ne touche
 * que ce qui a ete autorise, c est-a-dire le public. Le contexte n est remplace que pour
 * la duree de la requete, jamais enregistre : la session garde la connexion partielle,
 * et l etape reste reprenable.</p>
 */
public class ConnexionPartielleHorsEtapes extends OncePerRequestFilter {

    private static final String CLE_ANONYME = "connexion-partielle-hors-etapes";

    private final SecurityContextHolderStrategy strategie =
            SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        SecurityContext courant = strategie.getContext();
        if (!(courant.getAuthentication() instanceof AuthentificationPartielle)
                || estUneEtape(requete)) {
            chaine.doFilter(requete, reponse);
            return;
        }
        SecurityContext anonyme = strategie.createEmptyContext();
        anonyme.setAuthentication(new AnonymousAuthenticationToken(CLE_ANONYME, "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        strategie.setContext(anonyme);
        try {
            chaine.doFilter(requete, reponse);
        } finally {
            strategie.setContext(courant);
        }
    }

    /** Les pages d erreur aussi : elles rendent l en-tete, donc ses compteurs. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static boolean estUneEtape(HttpServletRequest requete) {
        String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
        return chemin.equals(EtapeConnexion.PREFIXE)
                || chemin.startsWith(EtapeConnexion.PREFIXE + "/");
    }
}
