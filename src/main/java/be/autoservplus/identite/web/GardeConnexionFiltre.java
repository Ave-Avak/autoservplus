package be.autoservplus.identite.web;

import be.autoservplus.config.EchecAuthentificationHandler;
import be.autoservplus.identite.service.GardeConnexion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Garde le POST du formulaire de connexion, avant le filtre qui authentifie.
 *
 * <p>Placé avant {@code UsernamePasswordAuthenticationFilter} : une tentative refusée ici
 * n'atteint ni BCrypt ni Spring Security, donc ne publie aucun événement d'échec et ne
 * touche aucun compteur de compte. Un tiers ne peut pas verrouiller un compte depuis une
 * IP deja au plafond.</p>
 *
 * <p>Il reconnaît le POST de connexion par le MÊME {@link RequestMatcher} que le filtre qui
 * authentifie : un chemin que l'un traiterait sans que l'autre le voie
 * ({@code /%63onnexion}, que Tomcat décode en {@code /connexion}) serait authentifié sans
 * plafond, sans défi et sans compte d'échec.</p>
 *
 * <p>Pas un composant Spring : déclaré comme tel, il serait aussi inscrit comme filtre de
 * servlet et s'appliquerait deux fois. {@code SecuriteConfig} l'instancie.</p>
 */
public class GardeConnexionFiltre extends OncePerRequestFilter {

    private static final Logger JOURNAL = LoggerFactory.getLogger(GardeConnexionFiltre.class);

    private final GardeConnexion garde;
    private final EchecAuthentificationHandler refus;
    private final RequestMatcher envoiConnexion;

    /**
     * @param envoiConnexion le matcher que porte aussi le filtre d'authentification
     */
    public GardeConnexionFiltre(GardeConnexion garde, EchecAuthentificationHandler refus,
                                RequestMatcher envoiConnexion) {
        this.garde = garde;
        this.refus = refus;
        this.envoiConnexion = envoiConnexion;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !envoiConnexion.matches(requete);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        if (garde.plafondAtteint(requete.getRemoteAddr())) {
            // Ni l'adresse saisie ni l'IP : la trace d'exploitation n'en a pas besoin.
            JOURNAL.info("Connexion refusee avant authentification, plafond d echecs par IP atteint.");
            refus.refuserSansCompter(requete, reponse);
            return;
        }
        chaine.doFilter(requete, reponse);
    }
}
