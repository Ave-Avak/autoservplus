package be.autoservplus.avis.service.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Note moyenne et volume d avis publies d une prestation (BL-4).
 *
 * <p>{@code moyenne} est {@code null} quand la prestation n a recu aucun avis : c est
 * la valeur que rend {@code AVG} sur un ensemble vide. Le distinguer de zero importe —
 * le CHECK {@code ck_avis_note} interdit la note zero, donc une moyenne nulle ne peut
 * pas signifier « tres mauvais », seulement « pas encore note ».</p>
 *
 * @param moyenne moyenne brute, ou {@code null} si aucun avis publie
 * @param nombre  nombre d avis publies pris en compte
 */
public record SyntheseAvis(Double moyenne, Long nombre) {

    /**
     * Types enveloppes et non primitifs : l expression de construction JPQL rend
     * {@code Double} pour AVG et {@code Long} pour COUNT. Un constructeur primitif
     * imposerait une surcharge, et les deux deviendraient ambigues a l appel.
     */
    public SyntheseAvis {
        nombre = nombre == null ? 0L : nombre;
    }

    public boolean aDesAvis() {
        return nombre > 0 && moyenne != null;
    }

    /**
     * Moyenne arrondie a une decimale, pour affichage. Rend {@code null} tant qu aucun
     * avis n a ete depose, afin que le gabarit affiche « pas encore note » plutot
     * qu un « 0,0 » qui se lirait comme un jugement.
     */
    public BigDecimal moyenneAffichee() {
        return aDesAvis()
                ? BigDecimal.valueOf(moyenne).setScale(1, RoundingMode.HALF_UP)
                : null;
    }

    /** Synthese vide, pour une prestation sans aucun avis. */
    public static SyntheseAvis vide() {
        return new SyntheseAvis(null, 0L);
    }
}
