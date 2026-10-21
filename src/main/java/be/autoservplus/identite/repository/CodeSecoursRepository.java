package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.CodeSecours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CodeSecoursRepository extends JpaRepository<CodeSecours, Long> {

    @Query("""
           select c from CodeSecours c
           where c.utilisateur.id = :compte and c.consommeLe is null and c.invalideLe is null
           order by c.id
           """)
    List<CodeSecours> utilisablesDe(@Param("compte") Long compte);

    /**
     * Invalide d un coup les codes encore utilisables : regeneration, desactivation,
     * reinitialisation, anonymisation. Les codes consommes gardent leur date.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           update CodeSecours c set c.invalideLe = :maintenant
           where c.utilisateur.id = :compte and c.consommeLe is null and c.invalideLe is null
           """)
    int invaliderUtilisables(@Param("compte") Long compte, @Param("maintenant") Instant maintenant);

    /**
     * Consommation atomique d un code : zero ligne modifiee signifie qu il a ete consomme
     * ou invalide entre la lecture et l ecriture. Pas de {@code clearAutomatically} : la
     * verification continue de modifier le compte, qui ne doit pas etre detache.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
           update CodeSecours c set c.consommeLe = :maintenant
           where c.id = :code and c.consommeLe is null and c.invalideLe is null
           """)
    int consommerSiUtilisable(@Param("code") Long code, @Param("maintenant") Instant maintenant);
}
