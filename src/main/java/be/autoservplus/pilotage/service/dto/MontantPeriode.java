package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;

/**
 * Un montant agrege sur une periode, avec le nombre de pieces comptables qui le
 * composent (BL-1).
 *
 * <p>Le compte accompagne toujours le montant : « 4 200 EUR » ne dit pas la meme
 * chose selon qu il vient d une facture ou de quarante.</p>
 */
public record MontantPeriode(BigDecimal htva, BigDecimal tvac, long nombre) {

    public boolean estVide() {
        return nombre == 0;
    }
}
