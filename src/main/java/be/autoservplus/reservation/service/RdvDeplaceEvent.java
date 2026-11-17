package be.autoservplus.reservation.service;

import java.util.UUID;

/**
 * Le garage a déplacé un rendez-vous (F16).
 *
 * <p>Distinct de {@link RdvStatutModifieEvent}, qui choisit le libellé d'après le
 * statut d'arrivée : un déplacement en sort toujours confirmé, et le membre aurait lu
 * « rendez-vous confirmé » pour un créneau qu'il n'a pas choisi. La référence seule,
 * comme les autres événements du projet.</p>
 */
public record RdvDeplaceEvent(UUID referenceRdv) {
}
