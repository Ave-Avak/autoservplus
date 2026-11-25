package be.autoservplus.catalogue.service.dto;

import java.math.BigDecimal;

/**
 * Prix pose seule demandé pour une prestation, ou son retrait.
 *
 * <p>Un porteur plutôt qu'un montant nullable : un appelant doit pouvoir dire « laisser
 * en l'état » (aucun porteur, l'import sans la colonne) aussi bien que « retirer »
 * ({@link #aucun()}, le formulaire au champ vide ou la cellule vide d'un import).</p>
 *
 * @param htva prix hors TVA, ou {@code null} pour ne plus proposer ce choix
 */
public record PrixPoseSeule(BigDecimal htva) {

    public static PrixPoseSeule aucun() {
        return new PrixPoseSeule(null);
    }

    public static PrixPoseSeule de(BigDecimal htva) {
        return new PrixPoseSeule(htva);
    }
}
