package be.autoservplus.common.debit;

import java.time.Duration;

/** Plafond d'une surface atteint : la requête reçoit 429 et l'attente en {@code Retry-After}. */
public class PlafondDepasseException extends RuntimeException {

    private final String surface;
    private final Duration attente;

    public PlafondDepasseException(String surface, Duration attente) {
        super("Plafond atteint sur " + surface);
        this.surface = surface;
        this.attente = attente;
    }

    public String surface() {
        return surface;
    }

    /** Secondes entières, arrondies au-dessus, une au moins : la valeur de {@code Retry-After}. */
    public long secondesAvantNouvelEssai() {
        long secondes = attente.toSeconds() + (attente.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, secondes);
    }
}
