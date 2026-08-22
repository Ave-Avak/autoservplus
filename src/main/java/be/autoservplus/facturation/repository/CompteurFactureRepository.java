package be.autoservplus.facturation.repository;

import be.autoservplus.facturation.domain.CompteurFacture;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompteurFactureRepository extends JpaRepository<CompteurFacture, Short> {

    /**
     * Verrou pessimiste sur la ligne de l exercice (SELECT ... FOR UPDATE) : les
     * emissions concurrentes sont serialisees le temps de lire, incrementer et
     * ecrire. Sans lui, deux transactions liraient la meme valeur et attribueraient
     * le meme numero — que l unicite {@code uq_facture_numero} refuserait ensuite,
     * transformant une course en erreur 500 au lieu d une file d attente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompteurFacture c WHERE c.exercice = :exercice")
    Optional<CompteurFacture> verrouillerParExercice(@Param("exercice") short exercice);

    /**
     * Cree la ligne de l exercice si elle n existe pas encore, sans jamais echouer
     * sur une course : deux premieres factures simultanees d une meme annee
     * passeraient toutes deux par un {@code save}, et la seconde violerait la cle
     * primaire — une violation de contrainte condamne la transaction PostgreSQL
     * entiere, donc l emission. Le {@code ON CONFLICT DO NOTHING} natif absorbe le
     * cas : le perdant attend le commit du gagnant puis ne fait rien, et trouve la
     * ligne au verrouillage qui suit.
     */
    @Modifying
    @Query(value = """
            INSERT INTO compteur_facture (exercice, dernier_numero)
            VALUES (:exercice, 0)
            ON CONFLICT (exercice) DO NOTHING
            """, nativeQuery = true)
    void creerSiAbsent(@Param("exercice") short exercice);
}
