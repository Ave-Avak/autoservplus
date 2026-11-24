package be.autoservplus.intervention.service.dto;

import java.util.List;

/**
 * Issue de la clôture d'une intervention.
 *
 * @param piecesRameneesAZero libellés, dans la langue de l'écran, des pièces dont le stock
 *                            ne couvrait pas la quantité posée et a été ramené à zéro
 *                            (Q-H-31). Le garage ne lit pas le journal : la confirmation de
 *                            la clôture les nomme.
 */
public record ClotureIntervention(String numero, List<String> piecesRameneesAZero) {

    public ClotureIntervention {
        piecesRameneesAZero = List.copyOf(piecesRameneesAZero);
    }
}
