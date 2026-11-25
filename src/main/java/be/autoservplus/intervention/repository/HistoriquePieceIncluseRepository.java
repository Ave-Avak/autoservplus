package be.autoservplus.intervention.repository;

import be.autoservplus.intervention.domain.HistoriquePieceIncluse;
import be.autoservplus.intervention.domain.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriquePieceIncluseRepository extends JpaRepository<HistoriquePieceIncluse, Long> {

    /** Trace d'une intervention dans l'ordre d'écriture, l'identifiant départageant le même instant. */
    List<HistoriquePieceIncluse> findByInterventionOrderByHorodatageAscIdAsc(Intervention intervention);
}
