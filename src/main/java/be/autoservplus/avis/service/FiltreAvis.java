package be.autoservplus.avis.service;

import java.util.Set;

/** État demandé au filtre de la modération des avis. */
public enum FiltreAvis {

    PUBLIE(Set.of(true), Set.of(true, false)),
    MASQUE(Set.of(false), Set.of(true, false)),
    SIGNALE(Set.of(true, false), Set.of(true));

    private static final Set<Boolean> TOUS = Set.of(true, false);

    private final Set<Boolean> publies;
    private final Set<Boolean> signales;

    FiltreAvis(Set<Boolean> publies, Set<Boolean> signales) {
        this.publies = publies;
        this.signales = signales;
    }

    /** Valeurs de {@code avis.publie} retenues, toutes sans filtre. */
    public static Set<Boolean> publies(FiltreAvis filtre) {
        return filtre == null ? TOUS : filtre.publies;
    }

    /** Valeurs de {@code avis.signale} retenues, toutes sans filtre. */
    public static Set<Boolean> signales(FiltreAvis filtre) {
        return filtre == null ? TOUS : filtre.signales;
    }
}
