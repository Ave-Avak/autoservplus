package be.autoservplus.catalogue.domain;

import java.util.Optional;

/**
 * Choix du client sur un forfait qui propose un prix pose seule : qui fournit les pièces.
 *
 * <p>Figé sur la ligne avec le prix, dans la colonne {@code pieces_client} (V77). Une ligne
 * sans choix proposé (pièce, prestation à l'heure, forfait sans prix pose seule) n'en porte
 * aucun : la colonne est nulle, et l'accesseur de la ligne rend un {@code Optional} vide.
 * Le booléen de la base ne sort pas de {@link ConvertisseurChoixPieces}.</p>
 */
public enum ChoixPieces {

    /** Le garage fournit les pièces, au prix pièces comprises, et les sort de son stock. */
    FOURNIES_PAR_GARAGE,

    /** Le client apporte ses pièces : prix pose seule, rien ne sort du stock. */
    APPORTEES_PAR_CLIENT;

    /** Clé i18n de la mention portée par les lignes et les documents. */
    public String cleMention() {
        return this == FOURNIES_PAR_GARAGE
                ? "choix-pieces.mention.fournies" : "choix-pieces.mention.apportees";
    }

    /** Clé de la mention d'une ligne, ou {@code null} quand aucun choix n'a été proposé. */
    public static String cleMentionDe(Optional<ChoixPieces> choix) {
        return choix.map(ChoixPieces::cleMention).orElse(null);
    }
}
