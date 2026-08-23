package be.autoservplus.i18n.web;

import be.autoservplus.config.InternationalisationConfig;
import be.autoservplus.i18n.LanguesSupportees;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;

/**
 * Expose a toutes les vues les liens du selecteur de langue (F6).
 *
 * <p>Un {@code @ControllerAdvice} <b>sans restriction de type</b>, contrairement aux
 * compteurs de panier et de notifications qui sont volontairement bornes : ceux-la
 * n ont de sens que dans un parcours, alors que le choix de la langue doit etre
 * offert sur chaque page. Un ecran oublie serait un cul-de-sac linguistique — on
 * pourrait y arriver en neerlandais et ne plus pouvoir en changer.</p>
 *
 * <p>Les adresses sont construites <b>au serveur</b> et non ecrites dans le gabarit,
 * pour une raison que le bandeau cookies a deja rencontree : la chaine de requete
 * doit etre conservee. Un lien ecrit {@code href="?lang=nl"} depuis une liste
 * filtree ou paginee renverrait sur la premiere page non filtree — changer de langue
 * ferait perdre sa place au visiteur.</p>
 *
 * <p>Aucun risque de redirection ouverte : ces adresses ne servent pas de cible de
 * redirection, elles pointent la page courante, et elles sont derivees de la requete
 * elle-meme et non d un parametre recopie.</p>
 */
@ControllerAdvice
public class SelecteurLangueAdvice {

    /**
     * Une langue proposee au selecteur.
     *
     * @param code    etiquette de langue, employee pour {@code lang} et la cle i18n
     * @param cle     cle du libelle, ecrit dans sa propre langue et non traduit
     * @param adresse page courante, dans cette langue
     * @param active  vrai pour la langue actuellement rendue
     */
    public record ChoixLangue(String code, String cle, String adresse, boolean active) {
    }

    @ModelAttribute
    public void exposerLeSelecteur(HttpServletRequest requete, Model modele) {
        Locale active = LocaleContextHolder.getLocale();
        modele.addAttribute("langueActive", active.getLanguage());
        modele.addAttribute("languesDisponibles", LanguesSupportees.admises().stream()
                .map(langue -> new ChoixLangue(
                        langue.getLanguage(),
                        "langue." + langue.getLanguage(),
                        pageCouranteEn(requete, langue.getLanguage()),
                        LanguesSupportees.estActive(active, langue)))
                .toList());
    }

    /**
     * Page courante avec le parametre de langue remplace. Le parametre est
     * <b>remplace</b> et non ajoute : sans cela, trois clics successifs produiraient
     * {@code ?lang=fr&lang=nl&lang=en}, dont seule la premiere valeur serait lue —
     * le selecteur cesserait de fonctionner apres le premier usage.
     */
    private String pageCouranteEn(HttpServletRequest requete, String code) {
        try {
            return UriComponentsBuilder.fromPath(requete.getRequestURI())
                    .query(requete.getQueryString())
                    .replaceQueryParam(InternationalisationConfig.PARAMETRE_LANGUE, code)
                    .build()
                    .toUriString();
        } catch (IllegalArgumentException chaineIllisible) {
            // La chaine de requete vient du client : une valeur mal encodee ne doit
            // pas faire echouer le rendu de TOUTES les pages. Le lien perd alors les
            // parametres, ce qui degrade le confort sans casser la fonction.
            return requete.getRequestURI()
                    + "?" + InternationalisationConfig.PARAMETRE_LANGUE + "=" + code;
        }
    }
}
