package be.autoservplus.intervention.domain;

/**
 * Nature d une ligne d intervention : soit main d oeuvre (une prestation du
 * catalogue), soit une piece detachee. Le type est deduit du champ non-null
 * cote entite ({@code prestation} ou {@code piece}), la table impose l un ou
 * l autre via un CHECK.
 */
public enum TypeLigneIntervention {
    MAIN_OEUVRE,
    PIECE
}
