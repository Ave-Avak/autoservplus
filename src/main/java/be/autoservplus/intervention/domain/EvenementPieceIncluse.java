package be.autoservplus.intervention.domain;

/**
 * Ce qui arrive à une pièce incluse, tel que l'écrit {@code historique_piece_incluse} (V77).
 * Le CHECK {@code ck_histo_incluse_quantites} lie chaque événement à ses quantités : aucune
 * avant une copie ou un ajout, aucune après un retrait, les deux pour une modification.
 */
public enum EvenementPieceIncluse {

    /** Composition de la prestation copiée sur la ligne, à sa création. */
    COPIE,

    /** Pièce ajoutée par le garage à une ligne qui ne la portait pas. */
    AJOUT,

    /** Quantité d'une pièce déjà incluse changée par le garage. */
    MODIFICATION,

    /** Pièce retirée de la ligne, ou ligne retirée avec ses pièces. */
    RETRAIT
}
