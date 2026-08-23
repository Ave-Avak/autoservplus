package be.autoservplus.api.web.dto;

/**
 * Un lien hypermedia de l API publique (BL-8).
 *
 * <p><b>Ecrit a la main plutot qu avec Spring HATEOAS.</b> Ajouter
 * {@code spring-boot-starter-hateoas} pour deux endpoints en lecture ferait entrer une
 * dependance, son format {@code _links} et sa serialisation HAL dans un projet qui
 * n en a besoin nulle part ailleurs. Le contrat rendu ici est volontairement plus
 * simple : une liste de couples {@code rel} / {@code href}, suffisante pour naviguer,
 * et qu on peut remplacer par HAL le jour ou l API grandit.</p>
 *
 * @param rel  relation au sens RFC 8288 : {@code self}, {@code next}, {@code prev}…
 * @param href URL absolue, construite depuis la requete courante
 */
public record Lien(String rel, String href) {
}
