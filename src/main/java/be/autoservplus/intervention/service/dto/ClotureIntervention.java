package be.autoservplus.intervention.service.dto;

import java.util.List;

/**
 * Issue de la clôture d'une intervention.
 *
 * @param piecesRameneesAZero libellés, dans la langue de l'écran, des pièces dont le stock
 *                            ne couvrait pas la quantité posée et a été ramené à zéro
 *                            (Q-H-31). Le garage ne lit pas le journal : la confirmation de
 *                            la clôture les nomme.
 * @param numeroFacture       facture d'atelier émise (A15, RM-17), ou {@code null} quand aucune
 *                            ligne n'est à facturer
 * @param neeDUneCommande     l'intervention vient d'une commande payée, déjà facturée : sans
 *                            ajout accepté, aucune facture complémentaire (Q-H-41)
 */
public record ClotureIntervention(String numero, List<String> piecesRameneesAZero, String numeroFacture,
                                  boolean neeDUneCommande) {

    public ClotureIntervention {
        piecesRameneesAZero = List.copyOf(piecesRameneesAZero);
    }

    /** Clôture sans facture d'une intervention qui ne vient pas d'une commande. */
    public ClotureIntervention(String numero, List<String> piecesRameneesAZero) {
        this(numero, piecesRameneesAZero, null, false);
    }
}
