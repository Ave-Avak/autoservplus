package be.autoservplus.common.numerotation;

/**
 * Suite numérotée par année au garage, tenue dans une table de compteurs qui lui est
 * propre.
 *
 * <p>Le nom de la table vient de cette énumération, jamais d'une chaîne fournie par
 * l'appelant : c'est ce qui permet de l'écrire dans la requête, où un nom de table ne
 * peut pas être un paramètre lié.</p>
 */
public enum TypeCompteur {

    /** Devis F15, {@code DEV-AAAA-NNNN} (V69). */
    DEVIS("compteur_devis", "DEV"),

    /** Fil de discussion, {@code FIL-AAAA-NNNN} (V71). */
    FIL("compteur_fil", "FIL"),

    /** Rendez-vous, {@code RDV-AAAA-NNNN} (V90). */
    RDV("compteur_rdv", "RDV"),

    /** Intervention, {@code INT-AAAA-NNNN} (V90). */
    INTERVENTION("compteur_intervention", "INT"),

    /** Commande de la boutique, {@code CMD-AAAA-NNNN} (V90). */
    COMMANDE("compteur_commande", "CMD");

    private final String table;
    private final String prefixe;

    TypeCompteur(String table, String prefixe) {
        this.table = table;
        this.prefixe = prefixe;
    }

    public String table() {
        return table;
    }

    public String prefixe() {
        return prefixe;
    }
}
