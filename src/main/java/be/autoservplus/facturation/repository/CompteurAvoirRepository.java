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
 * Les deux ne sont pas factorises : ils verrouillent deux tables distinctes, et
 * partager le code obligerait a partager le type d entite — donc a serialiser
 * l emission des factures et celle des avoirs sur un meme verrou, alors que rien
 * ne les lie.
 */
public interface CompteurAvoirRepository extends JpaRepository<CompteurAvoir, Short> {

    /**
     * Verrou pessimiste sur la ligne de l exercice (SELECT ... FOR UPDATE) : les
     * emissions concurrentes sont serialisees le temps de lire, incrementer et
     * ecrire. Sans lui, deux transactions liraient la meme valeur et attribueraient
     * le meme numero — que l unicite {@code uq_avoir_numero} refuserait ensuite,
     * transformant une course en erreur au lieu d une file d attente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompteurAvoir c WHERE c.exercice = :exercice")
    Optional<CompteurAvoir> verrouillerParExercice(@Param("exercice") short exercice);

    /**
     * Cree la ligne de l exercice si elle n existe pas encore, sans jamais echouer
     * sur une course : deux premiers avoirs simultanes d une meme annee passeraient
     * tous deux par un {@code save}, et le second violerait la cle primaire — une
     * violation de contrainte condamne la transaction PostgreSQL entiere, donc
     * l emission. Le {@code ON CONFLICT DO NOTHING} natif absorbe le cas.
     */
    @Modifying
    @Query(value = """
            INSERT INTO compteur_avoir (exercice, dernier_numero)
            VALUES (:exercice, 0)
            ON CONFLICT (exercice) DO NOTHING
            """, nativeQuery = true)
    void creerSiAbsent(@Param("exercice") short exercice);
}
