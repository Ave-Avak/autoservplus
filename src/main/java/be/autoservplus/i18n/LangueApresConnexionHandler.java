package be.autoservplus.i18n;

import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.web.DestinationApresConnexion;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.io.IOException;
import java.util.Locale;

/**
 * Applique à la session la langue enregistrée au profil, au moment de la
 * connexion et à ce moment-là seulement (F6).
 *
 * <p>La restriction au seul événement de connexion n'est pas un détail
 * d'implémentation, c'est la règle. Une première version appliquait la préférence à
 * chaque requête authentifiée dont la session n'avait pas de langue choisie : quatre
 * tests déjà en place sont passés au rouge et ont montré pourquoi c'était faux.
 * {@code utilisateur.langue} vaut {@code fr} par défaut pour tout le monde et
 * aucun écran ne l'écrit. La generalisation revenait donc à servir du
 * français à tout membre connecté, y compris celui dont le navigateur réclame du
 * néerlandais et qui n'a jamais rien choisi. C'était une régression déguisée en
 * fonctionnalité. Appliqué à la connexion seule, le profil ne prend la main qu'au
 * moment où l'on sait qui est la personne, et l'en-tête du navigateur continue de
 * décider partout ailleurs.</p>
 *
 * <p>La garde reste l'attribut de session du resolveur : il n'est écrit que lorsqu'une
 * langue a été explicitement choisie. Un visiteur qui bascule en néerlandais
 * puis se connecte garde donc son néerlandais : la protection contre la fixation de
 * session recopie les attributs, et celui-ci survit à la migration.</p>
 *
 * <p>Le resolveur est injecté et non lu par {@code RequestContextUtils} : cet
 * utilitaire s'appuie sur un attribut de requête posé par le {@code DispatcherServlet},
 * or un POST vers {@code /connexion} est traité par la chaîne de filtres de sécurité et
 * n'atteint jamais le servlet. La version qui l'employait échouait sans bruit : la
 * langue n'était pas appliquée, et seul un test de bout en bout passant
 * par le vrai formulaire l'a montré.</p>
 *
 * <p>La redirection mène à la page demandée avant la connexion quand elle est une page
 * du site, sinon à la page d'arrivée du rôle : {@link DestinationApresConnexion} en
 * décide, ici comme à la fin d'une connexion en plusieurs étapes.</p>
 */
@Component
public class LangueApresConnexionHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger JOURNAL = LoggerFactory.getLogger(LangueApresConnexionHandler.class);

    private final UtilisateurRepository utilisateurs;
    private final LocaleResolver resolveur;
    private final DestinationApresConnexion destination;

    public LangueApresConnexionHandler(UtilisateurRepository utilisateurs,
                                       LocaleResolver resolveur, DestinationApresConnexion destination) {
        this.utilisateurs = utilisateurs;
        this.resolveur = resolveur;
        this.destination = destination;
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication authentification) {
        return destination.de(requete, reponse, authentification);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication authentification)
            throws IOException, ServletException {
        appliquerLaPreference(requete, reponse, authentification);
        super.onAuthenticationSuccess(requete, reponse, authentification);
    }

    /**
     * L'échec est avale après journalisation : la connexion vient de reussir, et
     * échouer ici renverrait l'utilisateur à l'écran de connexion pour une question
     * de langue. Même arbitrage que le courriel informationnel d {@code AdminRdvService} :
     * l'accessoire ne fait pas tomber le principal.
     *
     * <p>Publique pour la connexion en plusieurs étapes : la langue s'applique quand la
     * connexion ABOUTIT, après le second facteur, et non dès le mot de passe.</p>
     */
    public void appliquerLaPreference(HttpServletRequest requete, HttpServletResponse reponse,
                                       Authentication authentification) {
        if (choixDejaExprime(requete)) {
            return;
        }
        try {
            utilisateurs.findByEmail(authentification.getName())
                    .map(membre -> Locale.forLanguageTag(membre.getLangue().name()))
                    .ifPresent(langue -> resolveur.setLocale(requete, reponse, langue));
        } catch (RuntimeException echec) {
            JOURNAL.warn("Langue du profil non appliquee a la connexion : {}", echec.getMessage());
        }
    }

    /** Vrai dès qu'une langue a été posée dans la session, quelle qu'en soit l'origine. */
    private boolean choixDejaExprime(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        return session != null
                && session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME) != null;
    }
}
