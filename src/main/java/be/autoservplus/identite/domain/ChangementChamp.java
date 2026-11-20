package be.autoservplus.identite.domain;

import java.util.Objects;

/**
 * Un champ du compte qui vient de changer, avec sa valeur avant et après.
 *
 * <p>Produit par l'entité au moment de la modification, et non recalculé par le
 * service : c'est l'entité qui sait quelles valeurs elle normalise, et un vide saisi
 * qui reste nul n'est pas un changement.</p>
 */
public record ChangementChamp(ChampCompte champ, String avant, String apres) {

    public ChangementChamp {
        Objects.requireNonNull(champ, "champ");
        if (Objects.equals(avant, apres)) {
            throw new IllegalArgumentException("Aucun changement sur " + champ + ".");
        }
    }
}
