package be.autoservplus.intervention.service;

import be.autoservplus.intervention.domain.UniteLigne;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Quantité d'une ligne d'intervention telle qu'on la lit : « 2 » pour une pièce ou un
 * forfait, « 1,5 h » pour des heures (« 1,5 u » en néerlandais), au séparateur décimal de la
 * langue. Le symbole d'unité suit la langue comme celui de l'euro dans {@code FormatageRdv}.
 */
public final class FormatageQuantite {

    private FormatageQuantite() {
    }

    public static String lisible(BigDecimal quantite, UniteLigne unite, Locale langue) {
        NumberFormat format = NumberFormat.getNumberInstance(langue);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        String nombre = format.format(quantite);
        if (unite != UniteLigne.HEURE) {
            return nombre;
        }
        return nombre + " " + ("nl".equals(langue.getLanguage()) ? "u" : "h");
    }
}
