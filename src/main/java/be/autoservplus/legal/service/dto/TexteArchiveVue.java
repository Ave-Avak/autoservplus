package be.autoservplus.legal.service.dto;

import java.time.Instant;
import java.util.List;

/**
 * Texte gele d une version, tel qu il est restitue a la consultation.
 *
 * <p>{@code langue} est celle du texte REELLEMENT servi, qui n est pas toujours celle
 * de la session : une version peut avoir ete publiee avant qu une langue ne soit
 * ajoutee. Afficher la langue evite le pire des cas — croire lire la traduction de son
 * choix alors qu on lit une autre langue.</p>
 *
 * @param languesDisponibles langues dans lesquelles cette version existe, pour que le
 *                           lecteur puisse demander celle qu il a effectivement acceptee
 */
public record TexteArchiveVue(String version,
                              Instant dateEffet,
                              String langue,
                              String contenu,
                              boolean actif,
                              List<String> languesDisponibles) {
}
