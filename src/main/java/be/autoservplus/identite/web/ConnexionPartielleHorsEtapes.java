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
 * Hors des étapes de connexion, une connexion PARTIELLE est vue comme anonyme.
 *
 * <p>Le jeton partiel porte le {@code UserDetails} du compte, pour que les étapes
 * sachent qui elles servent. Sur une route publique, il était donc lu comme une
 * connexion : {@code @AuthenticationPrincipal} le resolvait, et le choix des cookies
 * s'enregistrait au nom du compte, les compteurs du panier et des notifications
 * s'affichaient, avant que le second facteur ait été fourni.</p>
 *
 * <p>Placé APRÈS {@code AuthorizationFilter} : les routes protégées ont déjà refusé le
 * jeton partiel et renvoyé à l'étape ({@code RefusAccesEnEtapes}). Ce filtre ne touche
 * que ce qui a été autorisé, c'est-à-dire le public. Le contexte n'est remplacé que pour
 * la durée de la requête, jamais enregistré : la session garde la connexion partielle,
 * et l'étape reste reprenable.</p>
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

    /** Les pages d'erreur aussi : elles rendent l'en-tête, donc ses compteurs. */
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
