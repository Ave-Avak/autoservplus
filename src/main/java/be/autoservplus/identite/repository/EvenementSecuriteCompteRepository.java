package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvenementSecuriteCompteRepository
        extends JpaRepository<EvenementSecuriteCompte, Long> {

    /** Du plus recent au plus ancien, auteur charge d un coup : meme parti que le journal V40. */
    @Query("""
           select e from EvenementSecuriteCompte e
           left join fetch e.auteur
           where e.utilisateur.id = :compte
           order by e.horodatage desc, e.id desc
           """)
    List<EvenementSecuriteCompte> evenementsDe(@Param("compte") Long compte);
}
