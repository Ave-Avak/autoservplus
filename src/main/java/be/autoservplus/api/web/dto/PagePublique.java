package be.autoservplus.api.web.dto;

import java.util.List;

/**
 * Une page de resultats de l API publique (BL-8).
 *
 * <p>Le nombre total et le nombre de pages accompagnent toujours le contenu : un
 * client qui ne recoit que {@code liens.next} ne sait pas s il lui reste deux pages ou
 * deux cents, et ne peut ni afficher une progression ni decider de tout charger.</p>
 *
 * @param page    index de la page rendue, a partir de zero
 * @param taille  nombre d elements demandes par page
 * @param total   nombre total d elements, toutes pages confondues
 * @param liens   navigation hypermedia ; {@code next} et {@code prev} n apparaissent
 *                que lorsqu ils menent quelque part
 */
public record PagePublique<T>(
        List<T> contenu,
        int page,
        int taille,
        long total,
        int nombreDePages,
        List<Lien> liens) {
}
