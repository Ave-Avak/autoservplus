package be.autoservplus.legal.service.dto;

import java.time.Instant;

/**
 * Version d un document actuellement en vigueur, telle qu affichee en tete de la page
 * publique : le lecteur doit pouvoir dire QUEL texte il est en train de lire, sans quoi
 * la mention de version sur sa preuve d acceptation ne lui apprend rien.
 */
public record VersionEnVigueurVue(String version, Instant dateEffet) {
}
