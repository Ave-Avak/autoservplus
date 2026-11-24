package be.autoservplus.intervention.service.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Facture d'atelier d'une intervention telle que l'écran la montre (A15, RM-17) : de quoi
 * en afficher le statut et en télécharger le PDF. Le PDF est celui de l'émission, figé : le
 * statut se lit ici, jamais sur le document (Q-H-49).
 *
 * @param cleStatut clé i18n du statut : à régler, ou rien à régler pour une facture à 0 €
 */
public record FactureAtelierVue(UUID reference, String numero, String montantTvac, LocalDate echeance,
                                String cleStatut) {
}
