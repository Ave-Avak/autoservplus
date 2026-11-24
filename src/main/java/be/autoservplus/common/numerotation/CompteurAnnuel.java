package be.autoservplus.common.numerotation;

import be.autoservplus.common.temps.FuseauGarage;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Attribue le numéro suivant d'une suite annuelle, format {@code PRÉFIXE-AAAA-NNNN}, la
 * suite repartant à 1 chaque année au garage. Sert les devis.
 *
 * <p>Une seule instruction lit et incrémente le compteur de l'année : l'insertion crée
 * la ligne de la première attribution de l'année, le conflit sur la clé incrémente la
 * ligne existante en la verrouillant. Deux attributions simultanées sont donc sérialisées
 * sans verrou explicite, là où un compteur lu puis écrit en deux temps exigerait un
 * {@code SELECT ... FOR UPDATE} précédé d'une création à part, comme pour les factures.</p>
 *
 * <p>Un devis n'est pas une pièce comptable : rien n'impose une suite sans
 * trou. Le compteur tourne pourtant dans la transaction de l'appelant
 * ({@code MANDATORY}), pour qu'une création annulée ne consomme pas de numéro. Les
 * factures, les avoirs et les commandes gardent leur propre numérotation.</p>
 */
@Component
public class CompteurAnnuel {

    private final EntityManager entityManager;
    private final Clock horloge;
    private final FuseauGarage fuseau;

    public CompteurAnnuel(EntityManager entityManager, Clock horloge, FuseauGarage fuseau) {
        this.entityManager = entityManager;
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String prochainNumero(TypeCompteur type) {
        // Année au garage, comme la date imprimée sur le document : l'horloge est en UTC.
        int annee = fuseau.annee(horloge.instant());
        String table = type.table();
        Number sequence = (Number) entityManager.createNativeQuery("""
                        INSERT INTO %1$s (exercice, dernier_numero) VALUES (:exercice, 1)
                        ON CONFLICT (exercice)
                            DO UPDATE SET dernier_numero = %1$s.dernier_numero + 1
                        RETURNING dernier_numero
                        """.formatted(table))
                .setParameter("exercice", (short) annee)
                .getSingleResult();
        return "%s-%d-%04d".formatted(type.prefixe(), annee, sequence.intValue());
    }
}
