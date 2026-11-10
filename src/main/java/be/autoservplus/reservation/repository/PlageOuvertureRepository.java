package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.PlageOuverture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlageOuvertureRepository extends JpaRepository<PlageOuverture, Long> {

    List<PlageOuverture> findByJourSemaineAndActifTrueOrderByHeureDebut(short jourSemaine);

    List<PlageOuverture> findAllByOrderByJourSemaineAscHeureDebutAsc();

    List<PlageOuverture> findByActifTrue();

    /**
     * Plage non supprimée. Requête JPQL et non {@code findById} : le chargement par
     * clé primaire ne garantit pas le filtre {@code @SQLRestriction}, une plage retirée
     * resterait modifiable par son adresse.
     */
    @Query("SELECT p FROM PlageOuverture p WHERE p.id = :id")
    Optional<PlageOuverture> parIdentifiant(@Param("id") Long id);
}