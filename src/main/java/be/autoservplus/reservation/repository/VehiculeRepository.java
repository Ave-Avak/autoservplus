package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acces au parc de vehicules des membres.
 *
 * <p>Le membre est charge par JOIN FETCH dans les requetes de consultation : la
 * conversion en objet de transfert a lieu hors de la session Hibernate, open-in-view
 * etant desactive.</p>
 */
public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {

    @Query("SELECT v FROM Vehicule v JOIN FETCH v.membre WHERE v.reference = :reference")
    Optional<Vehicule> findByReference(@Param("reference") UUID reference);

    @Query("""
            SELECT v FROM Vehicule v JOIN FETCH v.membre m
            WHERE m.email = :email AND v.actif = true
            ORDER BY v.marque, v.modele
            """)
    List<Vehicule> findByMembre(@Param("email") String email);

    /** Vrai si la plaque est deja enregistree parmi les vehicules non supprimes. */
    boolean existsByPlaque(String plaque);

    long countByMembreEmailAndActifTrue(String email);
}