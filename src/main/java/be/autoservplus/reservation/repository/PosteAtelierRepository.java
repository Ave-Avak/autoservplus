package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.PosteAtelier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosteAtelierRepository extends JpaRepository<PosteAtelier, Long> {

    Optional<PosteAtelier> findByReference(UUID reference);

    /** Postes planifiables, dans l ordre d affichage choisi par l admin. */
    List<PosteAtelier> findByActifTrueOrderByOrdreAscLibelleAsc();

    List<PosteAtelier> findAllByOrderByOrdreAscLibelleAsc();
}