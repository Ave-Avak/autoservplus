package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.PlageOuverture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlageOuvertureRepository extends JpaRepository<PlageOuverture, Long> {

    List<PlageOuverture> findByJourSemaineAndActifTrueOrderByHeureDebut(short jourSemaine);

    List<PlageOuverture> findAllByOrderByJourSemaineAscHeureDebutAsc();
}