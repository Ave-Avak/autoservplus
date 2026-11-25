package be.autoservplus.intervention.repository;

import be.autoservplus.intervention.domain.Intervention;

import java.util.Optional;
import java.util.UUID;

/**
 * Verrou d'écriture d'une intervention, pris avant toute écriture qui ne doit croiser aucune
 * autre : la clôture, qui décompte le stock. Fragment de {@link InterventionRepository}.
 */
public interface VerrouIntervention {

    /**
     * L'intervention, ligne verrouillée jusqu'à la fin de la transaction et version montée.
     * L'attente est bornée par {@code autoservplus.interventions.attente-verrou}.
     *
     * @throws be.autoservplus.common.exception.ConflitConcurrenceException verrou non obtenu
     *         dans le délai, ou intervention lue périmée dans la transaction
     */
    Optional<Intervention> verrouiller(UUID reference);
}
