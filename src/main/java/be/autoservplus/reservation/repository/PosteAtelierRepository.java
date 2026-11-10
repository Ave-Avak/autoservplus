package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.PosteAtelier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosteAtelierRepository extends JpaRepository<PosteAtelier, Long> {

    Optional<PosteAtelier> findByReference(UUID reference);

    /** Postes planifiables, dans l ordre d affichage choisi par l admin. */
    List<PosteAtelier> findByActifTrueOrderByOrdreAscLibelleAsc();

    List<PosteAtelier> findAllByOrderByOrdreAscLibelleAsc();

    boolean existsByLibelleIgnoreCase(String libelle);

    /** Variante pour la modification : le poste édité ne se bloque pas lui-même. */
    boolean existsByLibelleIgnoreCaseAndReferenceNot(String libelle, UUID reference);

    /**
     * Nombre de rendez-vous qui référencent le poste, supprimés logiquement compris
     * (RM-29). Requête native, miroir de la seule FK {@code ON DELETE RESTRICT} vers
     * {@code poste_atelier}, {@code rdv_poste_id_fkey}. Les indisponibilités du poste,
     * en {@code ON DELETE CASCADE}, ne bloquent pas : elles n'ont pas de sens sans lui.
     */
    @Query(value = "SELECT count(*) FROM rdv WHERE poste_id = :id", nativeQuery = true)
    long nombreRendezVous(@Param("id") Long id);
}