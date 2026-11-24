package be.autoservplus.devis.service;

import be.autoservplus.common.temps.FuseauGarage;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Attribue le numéro d'un devis, format {@code DEV-AAAA-NNNN}, la suite repartant à 1
 * chaque année au garage.
 *
 * <p>Une seule instruction lit et incrémente le compteur de l'année : l'insertion crée
 * la ligne de la première émission de l'année, le conflit sur la clé incrémente la
 * ligne existante en la verrouillant. Deux émissions simultanées sont donc sérialisées
 * sans verrou explicite, là où un compteur lu puis écrit en deux temps exigerait un
 * {@code SELECT ... FOR UPDATE} précédé d'une création à part, comme pour les factures.</p>
 *
 * <p>Un devis n'est pas une pièce comptable : rien n'impose une suite sans trou. Le
 * compteur tourne pourtant dans la transaction de l'émission ({@code MANDATORY}), pour
 * qu'une émission annulée ne consomme pas de numéro.</p>
 */
@Component
public class GenerateurNumeroDevis {

    private final EntityManager entityManager;
    private final Clock horloge;
    private final FuseauGarage fuseau;

    public GenerateurNumeroDevis(EntityManager entityManager, Clock horloge, FuseauGarage fuseau) {
        this.entityManager = entityManager;
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String prochain() {
        // Année au garage, comme la date imprimée sur le devis : l'horloge est en UTC.
        int annee = fuseau.annee(horloge.instant());
        Number sequence = (Number) entityManager.createNativeQuery("""
                        INSERT INTO compteur_devis (exercice, dernier_numero) VALUES (:exercice, 1)
                        ON CONFLICT (exercice)
                            DO UPDATE SET dernier_numero = compteur_devis.dernier_numero + 1
                        RETURNING dernier_numero
                        """)
                .setParameter("exercice", (short) annee)
                .getSingleResult();
        return "DEV-%d-%04d".formatted(annee, sequence.intValue());
    }
}
