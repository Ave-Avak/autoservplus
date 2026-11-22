package be.autoservplus.common.web;

/**
 * Numéro de page reçu d'une adresse, ramené à ce qu'une liste peut servir.
 *
 * <p>Le décalage d'une requête paginée, numéro multiplié par la taille de la page, doit
 * tenir dans un entier. Au-delà, la requête est refusée avant d'atteindre la base et la
 * page répond 500, ce que donnait {@code /recherche?pagePrestations=2147483647}. Un
 * numéro négatif devient la première page. Un numéro trop grand est plafonné : la liste
 * rend alors une page vide, ou sa dernière page si elle sait y revenir.</p>
 */
public final class NumeroPage {

    private NumeroPage() {
    }

    /**
     * @param numero numéro à partir de zéro, tel que reçu
     * @param taille lignes par page, au moins une
     */
    public static int borne(int numero, int taille) {
        return Math.clamp(numero, 0, Integer.MAX_VALUE / taille - 1);
    }
}
