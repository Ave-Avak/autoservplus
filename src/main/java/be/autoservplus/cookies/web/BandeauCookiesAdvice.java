package be.autoservplus.cookies.web;

import be.autoservplus.common.web.FormulairePublic;
import be.autoservplus.common.web.PageCourante;
import be.autoservplus.cookies.domain.PreferencesCookies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;

import java.util.Optional;

/**
 * Expose a toutes les vues l etat du choix cookies (F25).
 *
 * <p><b>La decision d afficher le bandeau se prend au rendu, cote serveur.</b> Un
 * masquage effectue apres coup en JavaScript ferait apparaitre le bandeau une
 * fraction de seconde a chaque page, y compris pour un visiteur ayant deja
 * repondu. L afficher au serveur ou pas du tout supprime ce clignotement au lieu
 * de le corriger. C est aussi ce qui permet a l ensemble de fonctionner sans une
 * ligne de JavaScript.</p>
 *
 * <p>Passer par un {@code @ControllerAdvice} evite d ajouter l attribut dans chacun
 * des controleurs : le bandeau doit apparaitre sur toutes les pages, publiques
 * comme authentifiees, et un controleur oublie serait une page sans bandeau,
 * defaut invisible en developpement et fautif en production.</p>
 */
@ControllerAdvice
public class BandeauCookiesAdvice {

    static final String ATTRIBUT_CHOIX_EXPRIME = "choixCookiesExprime";
    static final String ATTRIBUT_PREFERENCES = "preferencesCookies";
    static final String ATTRIBUT_RETOUR = "uriRetourCookies";

    @ModelAttribute
    public void exposerEtatDesCookies(HttpServletRequest requete, Model modele) {
        Optional<PreferencesCookies> choix = choixExprime(requete);
        modele.addAttribute(ATTRIBUT_CHOIX_EXPRIME, choix.isPresent());
        // Aucun choix connu : les finalites optionnelles sont presentees refusees.
        // Une case pre-cochee vaudrait consentement implicite, ce que l Autorite de
        // Protection des Donnees refuse expressement.
        modele.addAttribute(ATTRIBUT_PREFERENCES, choix.orElseGet(PreferencesCookies::refusTotal));
        modele.addAttribute(ATTRIBUT_RETOUR, uriCourante(requete));
        if (choix.isEmpty() || porteUnFormulairePublic(requete)) {
            chargerLeJetonCsrf(requete);
        }
    }

    /** La méthode de contrôleur retenue pour la requête est marquée {@link FormulairePublic}. */
    private static boolean porteUnFormulairePublic(HttpServletRequest requete) {
        return requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode
                && methode.hasMethodAnnotation(FormulairePublic.class);
    }

    /**
     * Le jeton CSRF d'un formulaire n'est calculé qu'à sa première lecture. Pour un
     * visiteur sans session, cette lecture en crée une, ce que le conteneur refuse dès
     * que la réponse a commencé à partir : une page dont le premier formulaire venait
     * après 8 ko sortait tronquée, sans fin de document. Le jeton est donc lu ici, avant
     * le rendu, tant que la session peut encore naître. Seulement quand la page va
     * rendre un formulaire à ce visiteur : le bandeau, pour qui n'a pas encore choisi,
     * ou le formulaire d'une page marquée {@link FormulairePublic}. Une page de simple
     * lecture ne donne pas de session à qui a déjà choisi.
     */
    private static void chargerLeJetonCsrf(HttpServletRequest requete) {
        if (requete.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken jeton) {
            jeton.getToken();
        }
    }

    private Optional<PreferencesCookies> choixExprime(HttpServletRequest requete) {
        return Optional.ofNullable(WebUtils.getCookie(requete, PreferencesCookies.NOM_COOKIE))
                .flatMap(cookie -> PreferencesCookies.depuisValeurCookie(cookie.getValue()));
    }

    /**
     * Adresse a laquelle ramener le visiteur apres son choix, pour qu il reprenne sa
     * lecture ou il l avait laissee : le bandeau ne doit rien interrompre. La chaine
     * de requete est conservee : sans elle, un choix exprime depuis une page de
     * resultats filtres renverrait sur la liste non filtree.
     */
    private String uriCourante(HttpServletRequest requete) {
        String chemin = PageCourante.chemin(requete);
        String parametres = PageCourante.parametres(requete);
        return parametres == null ? chemin : chemin + "?" + parametres;
    }
}
