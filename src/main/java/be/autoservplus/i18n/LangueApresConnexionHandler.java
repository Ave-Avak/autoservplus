package be.autoservplus.i18n;

import be.autoservplus.identite.repository.UtilisateurRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.io.IOException;
import java.util.Locale;

/**
 * Applique a la session la langue enregistree au profil, <b>au moment de la
 * connexion</b> et a ce moment-la seulement (F6).
 *
 * <p>La restriction au seul evenement de connexion n est pas un detail
 * d implementation, c est la regle. Une premiere version appliquait la preference a
 * chaque requete authentifiee dont la session n avait pas de langue choisie : quatre
 * tests deja en place sont passes au rouge et ont montre pourquoi c etait faux.
 * {@code utilisateur.langue} vaut {@code fr} par defaut pour tout le monde et
 * <b>aucun ecran ne l ecrit</b> ; la generalisation revenait donc a servir du
 * francais a tout membre connecte, y compris celui dont le navigateur reclame du
 * neerlandais et qui n a jamais rien choisi. C etait une regression deguisee en
 * fonctionnalite. Applique a la connexion seule, le profil ne prend la main qu au
 * moment ou l on sait qui est la personne, et l en-tete du navigateur continue de
 * decider partout ailleurs.</p>
 *
 * <p>La garde reste l attribut de session du resolveur : il n est ecrit que lorsqu une
 * langue a ete <b>explicitement choisie</b>. Un visiteur qui bascule en neerlandais
 * puis se connecte garde donc son neerlandais — la protection contre la fixation de
 * session recopie les attributs, l attribut survit a la migration.</p>
 *
 * <p>Le resolveur est <b>injecte</b> et non lu par {@code RequestContextUtils} : cet
 * utilitaire s appuie sur un attribut de requete pose par le {@code DispatcherServlet},
 * or un POST vers {@code /connexion} est traite par la chaine de filtres de securite et
 * n atteint jamais le servlet. La version qui l employait echouait silencieusement — la
 * langue n etait tout simplement pas appliquee, et seul un test de bout en bout passant
 * par le vrai formulaire l a montre.</p>
 *
 * <p>Le comportement de redirection est identique a celui de
 * {@code defaultSuccessUrl("/mon-compte", true)} qu il remplace : meme classe parente,
 * memes deux reglages. Seule l application de la langue s y ajoute.</p>
 */
@Component
public class LangueApresConnexionHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger JOURNAL = LoggerFactory.getLogger(LangueApresConnexionHandler.class);

    private final UtilisateurRepository utilisateurs;
    private final LocaleResolver resolveur;

    public LangueApresConnexionHandler(UtilisateurRepository utilisateurs,
                                       LocaleResolver resolveur) {
        this.utilisateurs = utilisateurs;
        this.resolveur = resolveur;
        setDefaultTargetUrl("/mon-compte");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication authentification)
            throws IOException, ServletException {
        appliquerLaPreference(requete, reponse, authentification);
        super.onAuthenticationSuccess(requete, reponse, authentification);
    }

    /**
     * L echec est avale apres journalisation : la connexion vient de reussir, et
     * echouer ici renverrait l utilisateur a l ecran de connexion pour une question
     * de langue. Meme arbitrage que le courriel informationnel d {@code AdminRdvService} :
     * l accessoire ne fait pas tomber le principal.
     */
    private void appliquerLaPreference(HttpServletRequest requete, HttpServletResponse reponse,
                                       Authentication authentification) {
        if (choixDejaExprime(requete)) {
            return;
        }
        try {
            utilisateurs.findByEmailIgnoreCase(authentification.getName())
                    .map(membre -> Locale.forLanguageTag(membre.getLangue().name()))
                    .ifPresent(langue -> resolveur.setLocale(requete, reponse, langue));
        } catch (RuntimeException echec) {
            JOURNAL.warn("Langue du profil non appliquee a la connexion : {}", echec.getMessage());
        }
    }

    /** Vrai des qu une langue a ete posee dans la session, quelle qu en soit l origine. */
    private boolean choixDejaExprime(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        return session != null
                && session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME) != null;
    }
}
