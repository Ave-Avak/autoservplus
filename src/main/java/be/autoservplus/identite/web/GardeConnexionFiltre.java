package be.autoservplus.identite.web;

import be.autoservplus.config.EchecAuthentificationHandler;
import be.autoservplus.identite.service.GardeConnexion;
import be.autoservplus.identite.service.VerificateurTurnstile;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Garde le POST du formulaire de connexion, avant le filtre qui authentifie.
 *
 * <p>Placé avant {@code UsernamePasswordAuthenticationFilter} : une tentative refusée ici
 * n'atteint ni BCrypt ni Spring Security, donc ne publie aucun événement d'échec et ne
 * touche aucun compteur de compte. Un tiers ne peut pas verrouiller un compte depuis une
 * IP déjà au plafond, ni sans résoudre le défi une fois la vérification humaine exigée.</p>
 *
 * <p>Sans clé Turnstile configurée, la vérification humaine n'est pas exigée : rien ne
 * permettrait de la satisfaire. {@code AvertissementTurnstile} le signale au démarrage.</p>
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
    private final Optional<VerificateurTurnstile> turnstile;

    /**
     * @param envoiConnexion le matcher que porte aussi le filtre d'authentification
     */
    public GardeConnexionFiltre(GardeConnexion garde, EchecAuthentificationHandler refus,
                                RequestMatcher envoiConnexion,
                                Optional<VerificateurTurnstile> turnstile) {
        this.garde = garde;
        this.refus = refus;
        this.envoiConnexion = envoiConnexion;
        this.turnstile = turnstile;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !envoiConnexion.matches(requete);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        String ip = requete.getRemoteAddr();
        if (garde.plafondAtteint(ip)) {
            // Ni l'adresse saisie ni l'IP : la trace d'exploitation n'en a pas besoin.
            JOURNAL.info("Connexion refusee avant authentification, plafond d echecs par IP atteint.");
            refus.refuserSansCompter(requete, reponse, false);
            return;
        }
        if (turnstile.isPresent()
                && garde.exigeVerificationHumaine(requete.getParameter("email"), ip)
                && !turnstile.get().jetonValide(
                        requete.getParameter(VerificateurTurnstile.CHAMP_JETON), ip)) {
            JOURNAL.info("Connexion refusee avant authentification, verification humaine absente ou refusee.");
            refus.refuserSansCompter(requete, reponse, true);
            return;
        }
        chaine.doFilter(requete, reponse);
    }
}
