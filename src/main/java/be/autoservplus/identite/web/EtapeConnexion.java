package be.autoservplus.identite.web;

/**
 * Étapes qui peuvent séparer le mot de passe d'une connexion aboutie, dans leur ordre.
 *
 * <p><b>L'ordre est une garde.</b> Pour un compte déjà enrôlé, le second facteur passe
 * AVANT le changement imposé : sinon, qui ne détient que le mot de passe pourrait le
 * remplacer par le sien. Pour un compte neuf, il n'y a pas encore de second facteur, et
 * l'ordre revient à « mot de passe d'abord, enrôlement ensuite ».</p>
 */
public enum EtapeConnexion {

    SECOND_FACTEUR("/connexion/etape/second-facteur"),
    CHANGEMENT_MOT_DE_PASSE("/connexion/etape/mot-de-passe"),
    ENROLEMENT("/connexion/etape/enrolement");

    /** Préfixe commun, seul autorisé à une connexion partielle. */
    public static final String PREFIXE = "/connexion/etape";

    private final String chemin;

    EtapeConnexion(String chemin) {
        this.chemin = chemin;
    }

    public String chemin() {
        return chemin;
    }
}
