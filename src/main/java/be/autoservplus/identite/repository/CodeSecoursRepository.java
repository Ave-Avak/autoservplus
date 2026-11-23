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
     * Invalide d'un coup les codes encore utilisables : régénération, désactivation,
     * réinitialisation, anonymisation. Les codes consommés gardent leur date.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           update CodeSecours c set c.invalideLe = :maintenant
           where c.utilisateur.id = :compte and c.consommeLe is null and c.invalideLe is null
           """)
    int invaliderUtilisables(@Param("compte") Long compte, @Param("maintenant") Instant maintenant);

    /**
     * Consommation atomique d'un code : zéro ligne modifiée signifie qu'il a été consommé
     * ou invalidé entre la lecture et l'écriture. Pas de {@code clearAutomatically} : la
     * vérification continue de modifier le compte, qui ne doit pas être détaché.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
           update CodeSecours c set c.consommeLe = :maintenant
           where c.id = :code and c.consommeLe is null and c.invalideLe is null
           """)
    int consommerSiUtilisable(@Param("code") Long code, @Param("maintenant") Instant maintenant);
}
