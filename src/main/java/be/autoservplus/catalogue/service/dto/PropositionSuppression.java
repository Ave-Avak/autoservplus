package be.autoservplus.catalogue.service.dto;

import java.util.UUID;

/**
 * Diagnostic RM-29 presente avant de retirer un element du catalogue (A3, A6).
 *
 * <p>Le systeme detecte lui-meme le cas et propose l action adequate : suppression
 * definitive apres confirmation quand aucun historique ne reference l element,
 * desactivation (RM-28) sinon. L ecran de confirmation n affiche que l action
 * permise — le service reste l arbitre final au POST.</p>
 */
public record PropositionSuppression(
        UUID reference,
        String identifiant,
        String libelle,
        long nombreReferences) {

    public boolean suppressionPossible() {
        return nombreReferences == 0;
    }
}
