package be.autoservplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identification legale du garage, telle qu elle doit figurer sur une facture
 * belge : denomination, siege, numero d entreprise (BCE) et numero de TVA sont des
 * mentions <b>obligatoires</b> (Code TVA art. 53, AR n°1 art. 5).
 *
 * <p>En configuration et non en base : la V1 est mono-tenant, ces valeurs sont
 * celles d un seul garage et ne changent pas a chaud. Chaque champ est surchargeable
 * par variable d environnement — aucune valeur d exploitation n est figee dans le
 * code, et les defauts livres sont des valeurs de demonstration explicites. Le
 * passage au multi-tenant (V2) deplacera ces donnees vers une table portant la cle
 * du garage ; c est alors le fournisseur qui changera, pas le document.</p>
 *
 * <p>Premier usage de {@code @ConfigurationProperties} dans le projet, qui n employait
 * jusqu ici que {@code @Value} : douze champs lies ensemble et valides d un bloc au
 * demarrage valent mieux que douze injections independantes qu on peut oublier
 * d aligner.</p>
 */
@ConfigurationProperties(prefix = "autoservplus.garage")
public record IdentiteGarage(
        /**
         * Nom commercial, celui sous lequel le garage se presente au public. Distinct de
         * la raison sociale : la mention legale du responsable de la publication nomme
         * l enseigne ET l entite juridique, parce que ce sont deux choses que le lecteur
         * doit pouvoir rapprocher lui-meme.
         */
        String nomCommercial,
        String raisonSociale,
        String rue,
        String numeroRue,
        String codePostal,
        String localite,
        String pays,
        /** Numero d entreprise a la Banque-Carrefour, format 0000.000.000. */
        String numeroBce,
        /** Numero de TVA intracommunautaire, format BE0000000000. */
        String numeroTva,
        String iban,
        String telephone,
        String courriel) {

    /** Adresse du siege sur une ligne, pour l en-tete du document. */
    public String adresseLisible() {
        return "%s %s, %s %s".formatted(rue, numeroRue, codePostal, localite);
    }
}
