package be.autoservplus.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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

    @ModelAttribute
    public void exposerLeCheminCourant(HttpServletRequest requete, Model modele) {
        modele.addAttribute("cheminCourant",
                requete.getRequestURI().substring(requete.getContextPath().length()));
    }
}
