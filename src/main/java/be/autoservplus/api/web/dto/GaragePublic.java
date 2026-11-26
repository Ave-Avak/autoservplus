package be.autoservplus.api.web.dto;

import be.autoservplus.config.IdentiteGarage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Le garage tel que l API publique l expose (BL-8).
 *
 * <p><b>Une collection d un seul element.</b> La V1 est mono-tenant : il n existe
 * qu un garage, decrit en configuration. L endpoint rend malgre tout une liste
 * paginee, et non un objet seul. C est le meme contrat que servira la V2
 * multi-tenant, et un client ecrit aujourd hui n aura pas a etre reecrit le jour ou
 * la liste en contiendra plusieurs.</p>
 *
 * <p>L'identifiant est le numéro d'entreprise, chiffres seuls. Il est unique en Belgique,
 * ne change pas avec l'enseigne, et se lit déjà dans le numéro de TVA. Une séquence de la
 * base aurait été plus courte, mais le garage n'a pas de ligne en base en V1, et la V2
 * aurait dû la renuméroter.</p>
 *
 * <p><b>Pas d'IBAN</b>, bien qu'il soit en configuration : c'est une coordonnée bancaire.
 * L'API publique sert à trouver et contacter le garage, pas à le payer.</p>
 *
 * @param liens liens HAL du garage (F41) : lui-même, la collection, son catalogue et ses
 *              pages sur le site
 */
public record GaragePublic(
        String identifiant,
        String raisonSociale,
        String adresse,
        String codePostal,
        String localite,
        String pays,
        String numeroTva,
        String telephone,
        String courriel,
        @JsonProperty("_links") Map<String, Lien> liens) {

    /**
     * Numéro d'entreprise sans points ni espaces, tel qu'il figure dans l'adresse du garage.
     * Sans numéro configuré (profil de test), un identifiant fixe garde la route joignable :
     * le profil prod refuse de démarrer sans numéro d'entreprise.
     */
    public static String identifiantDe(IdentiteGarage identite) {
        String chiffres = identite.numeroBce() == null ? "" : identite.numeroBce().replaceAll("\\D", "");
        return chiffres.isEmpty() ? "garage" : chiffres;
    }

    public static GaragePublic de(IdentiteGarage identite, Map<String, Lien> liens) {
        return new GaragePublic(
                identifiantDe(identite),
                identite.raisonSociale(),
                "%s %s".formatted(identite.rue(), identite.numeroRue()),
                identite.codePostal(),
                identite.localite(),
                identite.pays(),
                identite.numeroTva(),
                identite.telephone(),
                identite.courriel(),
                liens);
    }
}
