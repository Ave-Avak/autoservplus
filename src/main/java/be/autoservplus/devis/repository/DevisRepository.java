package be.autoservplus.devis.repository;

import be.autoservplus.devis.domain.Devis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevisRepository extends JpaRepository<Devis, Long> {

    /** Devis du membre, le plus récent d'abord. */
    @Query("""
            SELECT d FROM Devis d
            WHERE d.membre.email = :email
            ORDER BY d.dateEmission DESC, d.id DESC
            """)
    List<Devis> duMembre(@Param("email") String email);

    /**
     * Un devis par sa référence, s'il appartient au membre. Celui d'un autre membre n'est
     * pas trouvé : la réponse est la même que pour une référence inconnue.
     */
    @Query("""
            SELECT d FROM Devis d LEFT JOIN FETCH d.lignes
            WHERE d.reference = :reference AND d.membre.email = :email
            """)
    Optional<Devis> duMembre(@Param("reference") UUID reference, @Param("email") String email);
}
