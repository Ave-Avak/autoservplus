package be.autoservplus.api.web.dto;

import be.autoservplus.config.IdentiteGarage;

/**
 * Le garage tel que l API publique l expose (BL-8).
 *
 * <p><b>Une collection d un seul element.</b> La V1 est mono-tenant : il n existe
 * qu un garage, decrit en configuration. L endpoint rend malgre tout une liste
 * paginee, et non un objet seul — c est le meme contrat que servira la V2
 * multi-tenant, et un client ecrit aujourd hui n aura pas a etre reecrit le jour ou
 * la liste en contiendra plusieurs.</p>
 *
 * <p><b>Ni IBAN ni numero BCE</b>, bien qu ils soient en configuration : le premier
 * est une coordonnee bancaire, le second n a d utilite que sur une facture. L API
 * publique sert a trouver et contacter le garage, pas a le payer.</p>
 */
public record GaragePublic(
        String raisonSociale,
        String adresse,
        String codePostal,
        String localite,
        String pays,
        String numeroTva,
        String telephone,
        String courriel) {

    public static GaragePublic de(IdentiteGarage identite) {
        return new GaragePublic(
                identite.raisonSociale(),
                "%s %s".formatted(identite.rue(), identite.numeroRue()),
                identite.codePostal(),
                identite.localite(),
                identite.pays(),
                identite.numeroTva(),
                identite.telephone(),
                identite.courriel());
    }
}
