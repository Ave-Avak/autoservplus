package be.autoservplus.devis.domain;

/**
 * Ce que porte une ligne de devis, reprise de la ligne du panier à l'émission (colonne
 * {@code ligne_devis.nature}, V78). Le devis n'a aucune clé vers l'article : sans elle, la
 * base ne saurait pas qu'un choix des pièces n'a de sens que sur une prestation.
 */
public enum NatureLigneDevis {
    PIECE,
    PRESTATION
}
