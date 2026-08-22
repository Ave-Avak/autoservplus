package be.autoservplus.vente.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Attribue le numero fonctionnel d une commande, format CMD-AAAA-NNNN.
 *
 * <p>Meme patron que {@code GenerateurNumeroIntervention} : la sequence
 * {@code seq_numero_commande} (V9, deja presente au socle) garantit l unicite
 * entre demandes concurrentes, l horloge injectee rend l annee testable.</p>
 */
@Component
public class GenerateurNumeroCommande {

    private final EntityManager entityManager;
    private final Clock horloge;

    public GenerateurNumeroCommande(EntityManager entityManager, Clock horloge) {
        this.entityManager = entityManager;
        this.horloge = horloge;
    }

    public String prochain() {
        Number sequence = (Number) entityManager
                .createNativeQuery("SELECT nextval('seq_numero_commande')")
                .getSingleResult();
        return "CMD-%d-%04d".formatted(Year.now(horloge).getValue(), sequence.longValue());
    }
}
