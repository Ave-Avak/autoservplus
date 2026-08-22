package be.autoservplus.vente.web;

import be.autoservplus.catalogue.web.CatalogueController;
import be.autoservplus.vente.service.PanierService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose le compteur d articles du panier ({@code nombreArticlesPanier}) aux pages
 * du parcours d achat : catalogue et panier. Volontairement limite a ces deux
 * controleurs — etendre le compteur a toute la navigation suppose d abord le
 * fragment d en-tete commun (dette documentee), sans quoi chaque page devrait etre
 * editee une a une.
 *
 * <p>Injection par {@link ObjectProvider} : les tests {@code @WebMvcTest} d autres
 * controleurs instancient tous les {@code @ControllerAdvice} du projet sans fournir
 * {@code PanierService} — le provider rend l advice inerte (compteur a 0) plutot
 * que de faire echouer leur contexte.</p>
 */
@ControllerAdvice(assignableTypes = {CatalogueController.class, PanierController.class})
public class PanierModelAdvice {

    private final ObjectProvider<PanierService> panierService;

    public PanierModelAdvice(ObjectProvider<PanierService> panierService) {
        this.panierService = panierService;
    }

    @ModelAttribute("nombreArticlesPanier")
    public int nombreArticlesPanier(Authentication authentification) {
        // L'anonyme de Spring Security repond isAuthenticated() = true : le test
        // d'instance est necessaire, comme dans JpaAuditingConfig.
        if (authentification == null
                || !authentification.isAuthenticated()
                || authentification instanceof AnonymousAuthenticationToken) {
            return 0;
        }
        PanierService service = panierService.getIfAvailable();
        return service == null ? 0 : service.nombreArticles(authentification.getName());
    }
}
