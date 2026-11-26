package be.autoservplus.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * Une page de résultats de l'API publique, au format HAL (F41).
 *
 * <p>Les éléments sont rangés sous {@code _embedded}, à la clé de la collection
 * ({@code prestations}, {@code articles}, {@code garages}), et chacun porte ses propres
 * liens. La navigation ({@code self}, {@code first}, {@code last}, {@code prev},
 * {@code next}) est sous {@code _links}. {@code prev} et {@code next} n'apparaissent que
 * lorsqu'ils mènent quelque part.</p>
 *
 * <p>Le nombre total et le nombre de pages accompagnent toujours le contenu : un client
 * qui ne reçoit que {@code next} ne sait pas s'il lui reste deux pages ou deux cents.</p>
 */
@JsonPropertyOrder({"_links", "_embedded", "page"})
public record PagePublique<T>(
        @JsonProperty("_links") Map<String, Lien> liens,
        @JsonProperty("_embedded") Map<String, List<T>> embarques,
        Pagination page) {

    /**
     * @param numero index de la page rendue, à partir de zéro
     * @param taille nombre d'éléments demandés par page
     * @param total  nombre d'éléments, toutes pages confondues
     */
    public record Pagination(int numero, int taille, long total, int nombreDePages) {
    }
}
