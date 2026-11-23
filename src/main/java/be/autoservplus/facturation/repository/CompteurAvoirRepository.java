package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.CompteurAvoir;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Jumeau de {@code CompteurFactureRepository} sur la table {@code compteur_avoir}.
 * Les deux ne sont pas factorisés : ils verrouillent deux tables distinctes, et
 * partager le code obligerait à partager le type d'entité, donc à sérialiser
 * l'émission des factures et celle des avoirs sur un même verrou, alors que rien
 * ne les lie.
 */
public interface CompteurAvoirRepository extends JpaRepository<CompteurAvoir, Short> {

    /**
     * Verrou pessimiste sur la ligne de l'exercice (SELECT ... FOR UPDATE) : les
     * émissions concurrentes sont sérialisées le temps de lire, incrémenter et
     * écrire. Sans lui, deux transactions liraient la même valeur et attribueraient
     * le même numéro : que l'unicité {@code uq_avoir_numero} refuserait ensuite,
     * transformant une course en erreur au lieu d'une file d'attente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompteurAvoir c WHERE c.exercice = :exercice")
    Optional<CompteurAvoir> verrouillerParExercice(@Param("exercice") short exercice);

    /**
     * Crée la ligne de l'exercice si elle n'existe pas encore, sans jamais échouer
     * sur une course : deux premiers avoirs simultanés d'une même année passeraient
     * tous deux par un {@code save}, et le second violerait la clé primaire. Or une
     * violation de contrainte condamne la transaction PostgreSQL entière, donc
     * l'émission. Le {@code ON CONFLICT DO NOTHING} natif absorbe le cas.
     */
    @Modifying
    @Query(value = """
            INSERT INTO compteur_avoir (exercice, dernier_numero)
            VALUES (:exercice, 0)
            ON CONFLICT (exercice) DO NOTHING
            """, nativeQuery = true)
    void creerSiAbsent(@Param("exercice") short exercice);
}
