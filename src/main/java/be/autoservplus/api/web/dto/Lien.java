package be.autoservplus.api.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Un lien HAL de l'API publique (F41), valeur d'une relation dans {@code _links}.
 *
 * <p>Écrit à la main plutôt qu'avec Spring HATEOAS : l'API compte quelques routes en
 * lecture, et le format HAL tient en trois types. La dépendance aurait apporté sa propre
 * sérialisation et son propre modèle de ressource, à faire cohabiter avec springdoc, pour
 * un gain nul ici.</p>
 *
 * @param href adresse absolue, construite depuis la requête courante
 * @param type type de la cible quand ce n'est pas l'API ({@code text/html} pour une page
 *             du site), absent sinon
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Lien(String href, String type) {

    public static Lien versApi(String href) {
        return new Lien(href, null);
    }

    public static Lien versPage(String href) {
        return new Lien(href, "text/html");
    }
}
