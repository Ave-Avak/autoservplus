package be.autoservplus.rgpd.repository;

import be.autoservplus.reservation.domain.Vehicule;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des vehicules d un membre pour l export du droit d acces (F22),
 * <b>suppressions logiques comprises</b>.
 *
 * <p>Repository propre au module rgpd plutot qu une methode ajoutee a
 * {@code VehiculeRepository} : le perimetre n est pas le meme.
 * {@code VehiculeRepository.findByMembre} alimente le formulaire de reservation et
 * ne rend que les vehicules actifs et non supprimes ; l article 15 porte sur les
 * donnees <i>detenues</i>, pas sur celles qui restent utilisables.
 *
 * <p><b>Requete native, et pourquoi.</b> Le {@code @SQLRestriction} de l entite
 * ajoute {@code deleted_at IS NULL} a toute requete HQL et a tout chargement par
 * identifiant ; il ne se desactive pas a la demande, contrairement a un
 * {@code @Filter} Hibernate. Passer l entite en {@code @Filter} aurait exige de
 * l activer dans <i>chaque</i> session du reste de l application, sous peine de
 * faire reapparaitre les vehicules supprimes dans le parc du membre et dans la
 * reservation : un risque disproportionne pour un besoin de lecture. Une requete
 * native ecrit son propre SQL, la restriction ne s y applique pas, et le
 * changement reste confine a ce fichier — le filtrage normal de l application est
 * intact.
 *
 * <p>La lecture reste sans danger : {@link Repository} n herite d aucune methode
 * d ecriture et le service est {@code readOnly}. Les entites rendues sont bien
 * gerees par la session, mais rien ne les modifie et aucun {@code flush} n a lieu.
 *
 * <p>Le filtre porte sur {@code membre_id} et non sur l adresse de courriel : le
 * SQL natif eviterait sinon une jointure vers {@code utilisateur}, elle-meme
 * soumise a sa propre suppression logique.
 */
public interface VehiculeExportRepository extends Repository<Vehicule, Long> {

    @Query(value = """
            SELECT v.* FROM vehicule v
            WHERE v.membre_id = :membreId
            ORDER BY v.id
            """, nativeQuery = true)
    List<Vehicule> pourMembre(@Param("membreId") Long membreId);
}
