package be.autoservplus.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Liens de pagination d'une liste, calculés depuis l'adresse de la requête.
 *
 * <p>Les liens reprennent tous les paramètres reçus et ne changent que {@code page}. Le
 * fragment {@code liens} de {@code pagination.html} nomme ses filtres un par un : chaque
 * liste qui en ajoute un devait l'y ajouter aussi, et un oubli faisait perdre le filtre
 * au changement de page sans qu'aucun test ne le voie. Les paramètres sont relus décodés
 * puis encodés une seule fois, pour qu'une recherche avec un espace ou un accent ne soit
 * pas encodée deux fois.</p>
 *
 * @param numero      numéro de la page affichée, à partir de 1
 * @param total       nombre de pages, au moins 1
 * @param precedente  adresse de la page précédente, ou {@code null} sur la première
 * @param suivante    adresse de la page suivante, ou {@code null} sur la dernière
 */
public record PaginationVue(int numero, int total, String precedente, String suivante) {

    private static final String PARAMETRE = "page";

    public static PaginationVue de(Page<?> resultats, HttpServletRequest requete) {
        int numero = resultats.getNumber() + 1;
        return new PaginationVue(numero, Math.max(1, resultats.getTotalPages()),
                resultats.hasPrevious() ? lien(requete, numero - 1) : null,
                resultats.hasNext() ? lien(requete, numero + 1) : null);
    }

    private static String lien(HttpServletRequest requete, int page) {
        UriComponentsBuilder adresse = UriComponentsBuilder.fromPath(requete.getRequestURI());
        requete.getParameterMap().forEach((nom, valeurs) -> {
            if (!PARAMETRE.equals(nom)) {
                adresse.queryParam(nom, (Object[]) valeurs);
            }
        });
        return adresse.queryParam(PARAMETRE, page).build().encode().toUriString();
    }
}
