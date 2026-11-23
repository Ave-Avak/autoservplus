package be.autoservplus.intervention.service;

import java.util.UUID;

/**
 * Événement applicatif publié quand une intervention passe en attente de l'accord du
 * membre sur un dépassement de devis (RM-15).
 *
 * <p>Ne transporte que la référence, jamais l'entité : l'écouteur s'exécute après la
 * validation de la transaction et recharge ce dont il a besoin dans la sienne.</p>
 */
public record DepassementSoumisEvent(UUID referenceIntervention) {
}
