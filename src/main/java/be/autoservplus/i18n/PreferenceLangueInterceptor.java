package be.autoservplus.i18n;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * Applique a la session la langue enregistree au profil du membre (F6).
 *
 * <p>La preference vit deja en base, colonne {@code utilisateur.langue}, et elle est
 * deja lue pour choisir la langue d une facture PDF. F6 ne cree donc pas une seconde
 * source de verite : il branche celle qui existait sur l interface web, qui jusqu ici
 * ne dependait que de l en-tete {@code Accept-Language} du navigateur.</p>
 *
 * <p><b>La garde est l attribut de session lui-meme</b>, et non un drapeau ajoute a
 * cote. {@link SessionLocaleResolver} n ecrit son attribut que lorsqu une langue a
 * ete <b>explicitement choisie</b> ; en heriter par defaut ne l ecrit pas. Sa
 * presence signifie donc exactement « quelqu un a deja tranche », et deux proprietes
 * en decoulent d un coup :</p>
 * <ul>
 *   <li>la base n est interrogee qu <b>une seule fois par session</b>, puisque
 *       l ecriture pose l attribut qui coupe les passages suivants ;</li>
 *   <li>un choix manuel au selecteur n est <b>jamais ecrase</b>, y compris celui
 *       exprime avant la connexion — c est la session courante qui prime, comme
 *       l attend quelqu un qui vient de cliquer sur « NL ».</li>
 * </ul>
 *
 * <p>Injection par {@link ObjectProvider}, comme les advices du projet : les tests
 * web a doublures construisent la configuration MVC sans fournir le depot, et
 * l intercepteur doit alors rester inerte plutot que de faire echouer leur
 * contexte.</p>
 */
public class PreferenceLangueInterceptor implements HandlerInterceptor {

    private final ObjectProvider<UtilisateurRepository> utilisateurs;

    public PreferenceLangueInterceptor(ObjectProvider<UtilisateurRepository> utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse,
                             Object gestionnaire) {
        if (choixDejaExprime(requete)) {
            return true;
        }
        langueDuProfil().ifPresent(langue -> appliquer(requete, reponse, langue));
        return true;
    }

    /** Vrai des qu une langue a ete posee dans la session, quelle qu en soit l origine. */
    private boolean choixDejaExprime(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        return session != null
                && session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME) != null;
    }

    private Optional<Locale> langueDuProfil() {
        UtilisateurRepository depot = utilisateurs.getIfAvailable();
        if (depot == null) {
            return Optional.empty();
        }
        return identifiantDuMembre()
                .flatMap(depot::findByEmailIgnoreCase)
                .map(Utilisateur::getLangue)
                .map(langue -> Locale.forLanguageTag(langue.name()));
    }

    private Optional<String> identifiantDuMembre() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        // L'anonyme de Spring Security repond isAuthenticated() = true : le test
        // d'instance est necessaire, comme dans PanierModelAdvice et JpaAuditingConfig.
        if (authentification == null
                || !authentification.isAuthenticated()
                || authentification instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentification.getName());
    }

    private void appliquer(HttpServletRequest requete, HttpServletResponse reponse, Locale langue) {
        LocaleResolver resolveur = RequestContextUtils.getLocaleResolver(requete);
        if (resolveur != null) {
            resolveur.setLocale(requete, reponse, langue);
        }
    }
}
