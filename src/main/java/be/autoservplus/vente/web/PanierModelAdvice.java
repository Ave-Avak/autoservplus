package be.autoservplus.vente.web;

import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.vente.service.PanierService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose le compteur d articles du panier ({@code nombreArticlesPanier}) a toutes les
 * pages, pour l en-tete commun. Il etait limite au parcours d achat tant que chaque
 * page recopiait son en-tete.
 *
 * <p>Une connexion partielle compte zero : le compteur n a rien a dire avant la fin de
 * la connexion.</p>
 *
 * <p>Injection par {@link ObjectProvider} : les tests {@code @WebMvcTest} d autres
 * controleurs instancient tous les {@code @ControllerAdvice} du projet sans fournir
 * {@code PanierService}. Le provider rend l advice inerte (compteur a 0) plutot
 * que de faire echouer leur contexte.</p>
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
