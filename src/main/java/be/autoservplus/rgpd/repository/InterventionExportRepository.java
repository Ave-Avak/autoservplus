package be.autoservplus.rgpd.repository;

import be.autoservplus.intervention.domain.Intervention;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Lecture des interventions d un membre pour l export (F22).
 *
 * <p>Le rattachement passe par les <b>identifiants de vehicule</b> du membre, et
 * non par une jointure vers {@code Vehicule} ni par le rendez-vous. Deux raisons,
 * chacune suffisante :
 * <ul>
 *   <li>{@code rdv_id} est nullable en base — une intervention peut naitre d une
 *       entree directe au garage — donc filtrer sur le RDV laisserait echapper des
 *       dossiers du membre le jour ou ce chemin de creation existera ;</li>
 *   <li>une jointure vers {@code Vehicule} porterait son {@code @SQLRestriction} :
 *       retirer un vehicule du parc effacerait ses interventions de l export, alors
 *       que la suppression logique existe precisement pour <i>preserver</i> cet
 *       historique. La liste d identifiants fournie par le service inclut les
 *       vehicules supprimes.</li>
 * </ul>
 *
 * <p>{@code i.vehicule.id} ne declenche aucune jointure : Hibernate lit la colonne
 * de cle etrangere directement, ce qui laisse la restriction hors du chemin.
 *
 * <p>Le RDV est charge en {@code LEFT JOIN} : nullable, et une jointure interne
 * ferait disparaitre les interventions sans rendez-vous.
 */
public interface InterventionExportRepository extends Repository<Intervention, Long> {

    /** L appelant garantit une collection d identifiants non vide. */
    @Query("""
            SELECT DISTINCT i FROM Intervention i
            LEFT JOIN FETCH i.rdv
            LEFT JOIN FETCH i.lignes
            WHERE i.vehicule.id IN :idsVehicules
            ORDER BY i.id
            """)
    List<Intervention> pourVehicules(@Param("idsVehicules") Collection<Long> idsVehicules);
}
