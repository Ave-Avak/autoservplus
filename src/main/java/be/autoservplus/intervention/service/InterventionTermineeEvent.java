package be.autoservplus.intervention.service;

import java.util.UUID;

/**
 * Evenement applicatif publie au passage d une intervention a TERMINEE (F17).
 *
 * <p>Ne transporte que la reference, jamais l entite : le listener s execute apres
 * commit, hors de la session Hibernate d origine, et une entite embarquee y leverait
 * {@code LazyInitializationException} a la premiere relation touchee. Le listener
 * recharge ce dont il a besoin dans sa propre transaction.</p>
 */
public record InterventionTermineeEvent(UUID referenceIntervention) {
}
