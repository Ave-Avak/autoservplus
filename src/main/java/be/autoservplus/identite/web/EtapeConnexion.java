package be.autoservplus.identite.web;

/**
 * Etapes qui peuvent separer le mot de passe d une connexion aboutie, dans leur ordre.
 *
 * <p><b>L ordre est une garde.</b> Pour un compte deja enrole, le second facteur passe
 * AVANT le changement impose : sinon, qui ne detient que le mot de passe pourrait le
 * remplacer par le sien. Pour un compte neuf, il n y a pas encore de second facteur, et
 * l ordre revient a « mot de passe d abord, enrolement ensuite ».</p>
 */
public enum EtapeConnexion {

    SECOND_FACTEUR("/connexion/etape/second-facteur"),
    CHANGEMENT_MOT_DE_PASSE("/connexion/etape/mot-de-passe"),
    ENROLEMENT("/connexion/etape/enrolement");

    /** Prefixe commun, seul autorise a une connexion partielle. */
    public static final String PREFIXE = "/connexion/etape";

    private final String chemin;

    EtapeConnexion(String chemin) {
        this.chemin = chemin;
    }

    public String chemin() {
        return chemin;
    }
}
