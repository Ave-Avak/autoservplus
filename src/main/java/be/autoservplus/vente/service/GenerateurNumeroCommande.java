package be.autoservplus.vente.service;

import be.autoservplus.common.temps.FuseauGarage;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Attribue le numéro fonctionnel d'une commande, format CMD-AAAA-NNNN.
 *
 * <p>Même patron que {@code GenerateurNumeroIntervention} : la séquence
 * {@code seq_numero_commande} (V9, déjà présente au socle) garantit l'unicité
 * entre demandes concurrentes, l'horloge injectée rend l'année testable.</p>
 */
@Component
public class GenerateurNumeroCommande {

    private final EntityManager entityManager;
    private final Clock horloge;
    private final FuseauGarage fuseau;

    public GenerateurNumeroCommande(EntityManager entityManager, Clock horloge,
                                    FuseauGarage fuseau) {
        this.fuseau = fuseau;
        this.entityManager = entityManager;
        this.horloge = horloge;
    }

    public String prochain() {
        Number sequence = (Number) entityManager
                .createNativeQuery("SELECT nextval('seq_numero_commande')")
                .getSingleResult();
        return "CMD-%d-%04d".formatted(fuseau.annee(horloge.instant()), sequence.longValue());
    }
}
