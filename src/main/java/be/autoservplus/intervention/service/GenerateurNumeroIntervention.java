package be.autoservplus.intervention.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Attribue le numero fonctionnel d une intervention, format INT-AAAA-NNNN.
 *
 * <p>La sequence {@code seq_numero_intervention} (V9) garantit l unicite entre
 * demandes concurrentes. L horloge est injectee pour rendre les tests
 * deterministes.</p>
 */
@Component
public class GenerateurNumeroIntervention {

    private final EntityManager entityManager;
    private final Clock horloge;

    public GenerateurNumeroIntervention(EntityManager entityManager, Clock horloge) {
        this.entityManager = entityManager;
        this.horloge = horloge;
    }

    public String prochain() {
        Number sequence = (Number) entityManager
                .createNativeQuery("SELECT nextval('seq_numero_intervention')")
                .getSingleResult();
        return "INT-%d-%04d".formatted(Year.now(horloge).getValue(), sequence.longValue());
    }
}
