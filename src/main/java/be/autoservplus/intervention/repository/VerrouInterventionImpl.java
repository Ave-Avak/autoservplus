package be.autoservplus.intervention.repository;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.intervention.domain.Intervention;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PessimisticLockException;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Verrou de {@link VerrouIntervention}, en deux temps.
 *
 * <p>D'abord {@code PESSIMISTIC_WRITE} ({@code FOR NO KEY UPDATE}), qui attend la ligne. Le
 * mode qui monterait la version d'un coup, {@code PESSIMISTIC_FORCE_INCREMENT}, Hibernate
 * l'écrit en {@code NOWAIT} sur PostgreSQL quel que soit le délai demandé : la seconde de deux
 * écritures échouerait aussitôt, sans attendre. Puis la version monte sous ce verrou, par
 * {@code PESSIMISTIC_FORCE_INCREMENT} sur une ligne déjà tenue, qui ne peut plus attendre ni
 * échouer pour une autre transaction. Une écriture concurrente qui a lu le dossier avant
 * échoue donc à sa propre vérification de version.</p>
 *
 * <p>L'attente est bornée par {@code lock_timeout}, posé pour la seule prise du verrou et
 * rendu ensuite à sa valeur : les verrous de pièces pris plus loin par la clôture gardent la
 * leur. Au-delà du délai, ou si le dossier déjà lu dans la transaction est périmé, le conflit
 * est traduit ici, au lieu de remonter en page d'erreur.</p>
 */
class VerrouInterventionImpl implements VerrouIntervention {

    @PersistenceContext
    private EntityManager em;

    private final Duration attente;

    VerrouInterventionImpl(@Value("${autoservplus.interventions.attente-verrou:3s}") Duration attente) {
        this.attente = attente;
    }

    @Override
    public Optional<Intervention> verrouiller(UUID reference) {
        String delaiPrecedent = (String) em.createNativeQuery("SELECT current_setting('lock_timeout')")
                .getSingleResult();
        delaiDuVerrou(attente.toMillis() + "ms");
        Optional<Intervention> verrouillee;
        try {
            verrouillee = em.createQuery("SELECT i FROM Intervention i WHERE i.reference = :reference",
                            Intervention.class)
                    .setParameter("reference", reference)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultStream()
                    .findFirst();
            verrouillee.ifPresent(it -> em.lock(it, LockModeType.PESSIMISTIC_FORCE_INCREMENT));
        } catch (RuntimeException echec) {
            if (!estUnConflit(echec)) {
                throw echec;
            }
            // La transaction est perdue pour PostgreSQL : le délai n'est pas rendu, elle s'annule.
            throw new ConflitConcurrenceException(
                    "Cette intervention est en cours de modification par un autre administrateur, rechargez la page.");
        }
        delaiDuVerrou(delaiPrecedent);
        return verrouillee;
    }

    /**
     * Délai dépassé ou dossier périmé, sous la forme JPA ou sous celle d'Hibernate : le délai
     * dépassé remonte en {@code org.hibernate.PessimisticLockException}, que la forme JPA ne
     * couvre pas, et finissait en page d'erreur.
     */
    private static boolean estUnConflit(RuntimeException echec) {
        return echec instanceof LockTimeoutException
                || echec instanceof PessimisticLockException
                || echec instanceof OptimisticLockException
                || echec instanceof org.hibernate.PessimisticLockException
                || echec instanceof org.hibernate.exception.LockAcquisitionException
                || echec instanceof org.hibernate.StaleStateException;
    }

    /** {@code set_config} plutôt que {@code SET LOCAL} : la valeur passe en paramètre. */
    private void delaiDuVerrou(String valeur) {
        em.createNativeQuery("SELECT set_config('lock_timeout', :valeur, true)")
                .setParameter("valeur", valeur)
                .getSingleResult();
    }
}
