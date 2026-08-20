package be.autoservplus.reservation.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Attribue le numero fonctionnel d un rendez-vous, format RDV-AAAA-NNNN.
 *
 * <p>L horloge est injectee afin que les tests puissent fixer l annee.</p>
 */
@Component
public class GenerateurNumeroRdv {

    private final EntityManager entityManager;
    private final Clock horloge;

    public GenerateurNumeroRdv(EntityManager entityManager, Clock horloge) {
        this.entityManager = entityManager;
        this.horloge = horloge;
    }

    public String prochain() {
        Number sequence = (Number) entityManager
                .createNativeQuery("SELECT nextval('rdv_numero_seq')")
                .getSingleResult();
        return "RDV-%d-%04d".formatted(Year.now(horloge).getValue(), sequence.longValue());
    }
}