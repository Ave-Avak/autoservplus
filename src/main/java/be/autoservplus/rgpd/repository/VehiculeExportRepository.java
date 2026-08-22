package be.autoservplus.rgpd.repository;

import be.autoservplus.reservation.domain.Vehicule;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des vehicules d un membre pour l export du droit d acces (F22).
 *
 * <p>Repository <b>propre au module rgpd</b> plutot qu une methode ajoutee a
 * {@code VehiculeRepository}. Deux raisons, et la seconde est la vraie :
 * <ul>
 *   <li>le perimetre differe — {@code VehiculeRepository.findByMembre} filtre sur
 *       {@code actif = true} parce qu il alimente le formulaire de reservation,
 *       alors qu un vehicule desactive reste une donnee detenue et doit sortir ;</li>
 *   <li>l export est une obligation legale dont la <b>completude doit s auditer en
 *       un seul endroit</b> : toutes ses requetes vivent ici, et une evolution des
 *       repositories metier ne peut pas retrecir silencieusement ce qui est
 *       communique a la personne.</li>
 * </ul>
 *
 * <p>Il etend {@link Repository} et non {@code JpaRepository} : aucune methode
 * d ecriture n est heritee, l export ne peut structurellement rien modifier.
 *
 * <p><b>Limite connue</b> : le {@code @SQLRestriction} de l entite masque les
 * vehicules supprimes logiquement. Ils restent detenus en base et echappent donc a
 * l export — point a documenter au rapport ecrit.
 */
public interface VehiculeExportRepository extends Repository<Vehicule, Long> {

    @Query("""
            SELECT v FROM Vehicule v
            WHERE LOWER(v.membre.email) = LOWER(:email)
            ORDER BY v.id
            """)
    List<Vehicule> pourMembre(@Param("email") String email);
}
