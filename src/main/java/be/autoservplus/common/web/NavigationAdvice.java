package be.autoservplus.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Chemin de la page rendue, pour que les menus marquent leur entree active
 * ({@code aria-current="page"}).
 *
 * <p>Thymeleaf 3.1 ne donne plus acces a la requete depuis les gabarits : l attribut
 * est donc pose ici, pour tous les controleurs. Le chemin est pris sans le
 * contexte de deploiement, pour etre compare aux adresses ecrites dans les fragments.
 * Absent quand un {@code @ExceptionHandler} rend la vue : aucune entree n y est alors
 * marquee, ce qui est exact.</p>
 */
@ControllerAdvice
public class NavigationAdvice {

    /** Rubrique du menu du site qui couvre plusieurs adresses : les quatre listes du catalogue et leurs fiches. */
    public static final String CATALOGUE = "catalogue";

    private static final List<String> PREFIXES_DU_CATALOGUE =
            List.of("/catalogue", "/services", "/pieces", "/accessoires", "/entretien");

    @ModelAttribute
    public void exposerLeCheminCourant(HttpServletRequest requete, Model modele) {
        String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
        modele.addAttribute("cheminCourant", chemin);
        modele.addAttribute("rubriqueCourante", rubrique(chemin));
    }

    /**
     * Rubrique du menu à marquer, ou {@code null}. L'entrée Catalogue mène à une seule
     * adresse mais reste marquée sur les quatre onglets et sur chaque fiche (Q-I1-2) :
     * le visiteur qui lit une fiche est toujours dans le catalogue.
     */
    public static String rubrique(String chemin) {
        if (chemin == null) {
            return null;
        }
        for (String prefixe : PREFIXES_DU_CATALOGUE) {
            if (chemin.equals(prefixe) || chemin.startsWith(prefixe + "/")) {
                return CATALOGUE;
            }
        }
        return null;
    }
}
