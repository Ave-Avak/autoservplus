package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.ChoixPieces;

import java.util.Optional;

/**
 * Prestation d'un rendez-vous ou d'une commande, telle que la montre un écran de détail : son
 * libellé et la mention du choix des pièces. Les listes s'en tiennent aux libellés.
 *
 * @param mentionPieces clé i18n de la mention, {@code null} quand aucun choix n'a été proposé
 */
public record PrestationRetenueVue(String libelle, String mentionPieces) {

    public static PrestationRetenueVue de(String libelle, Optional<ChoixPieces> choix) {
        return new PrestationRetenueVue(libelle, ChoixPieces.cleMentionDe(choix));
    }
}
