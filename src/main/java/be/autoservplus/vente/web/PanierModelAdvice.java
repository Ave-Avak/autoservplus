package be.autoservplus.vente.web;

import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.vente.service.PanierService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose le compteur d'articles du panier ({@code nombreArticlesPanier}) à toutes les
 * pages, pour l'en-tête commun. Il était limité au parcours d'achat tant que chaque
 * page recopiait son en-tête.
 *
 * <p>Une connexion partielle compte zéro : le compteur n'a rien à dire avant la fin de
 * la connexion.</p>
 *
 * <p>Injection par {@link ObjectProvider} : les tests {@code @WebMvcTest} d'autres
 * contrôleurs instancient tous les {@code @ControllerAdvice} du projet sans fournir
 * {@code PanierService}. Le provider rend l'advice inerte (compteur à 0) plutôt
 * que de faire échouer leur contexte.</p>
 */
@ControllerAdvice
public class PanierModelAdvice {

    private final ObjectProvider<PanierService> panierService;

    public PanierModelAdvice(ObjectProvider<PanierService> panierService) {
        this.panierService = panierService;
    }

    @ModelAttribute("nombreArticlesPanier")
    public int nombreArticlesPanier(Authentication authentification) {
        if (!AuthentificationPartielle.aboutie(authentification)) {
            return 0;
        }
        PanierService service = panierService.getIfAvailable();
        return service == null ? 0 : service.nombreArticles(authentification.getName());
    }
}
