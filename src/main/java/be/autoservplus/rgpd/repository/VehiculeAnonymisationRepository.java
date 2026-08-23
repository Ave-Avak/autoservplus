package be.autoservplus.rgpd.repository;

import be.autoservplus.reservation.domain.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acces au parc d un membre pour la suppression de compte (F23).
 *
 * <p>Repository propre au module rgpd plutot qu une methode ajoutee a
 * {@code VehiculeRepository}, meme raison que pour {@link VehiculeExportRepository} :
 * le perimetre n est pas celui du parc utilisable. {@code findByMembre} ne rend que
 * les vehicules actifs et non supprimes ; l anonymisation doit atteindre <b>tous</b>
 * ceux qui portent encore une plaque, y compris ceux que le membre avait retires.
 * Un vehicule supprime logiquement garde sa plaque en base : le laisser intact
 * viderait le compte sans vider la donnee.</p>
 *
 * <p><b>Requetes natives</b> : le {@code @SQLRestriction} de l entite ajoute
 * {@code deleted_at IS NULL} a toute requete HQL et ne se desactive pas a la demande.
 * Le SQL natif ecrit son propre filtre, et le changement reste confine a ce
 * fichier.</p>
 */
public interface VehiculeAnonymisationRepository extends JpaRepository<Vehicule, Long> {

    /**
     * Tous les vehicules du membre, suppressions logiques comprises, tries pour un
     * traitement deterministe.
     */
    @Query(value = """
            SELECT v.* FROM vehicule v
            WHERE v.membre_id = :membreId
            ORDER BY v.id
            """, nativeQuery = true)
    List<Vehicule> tousLesVehicules(@Param("membreId") Long membreId);

    /**
     * Nombre de lignes d historique referencant le vehicule. Miroir applicatif exact
     * des trois FK {@code ON DELETE RESTRICT} qui pointent {@code vehicule}
     * ({@code fk_rdv_vehicule}, {@code fk_intervention_vehicule},
     * {@code fk_resa_parking_vehicule}), sur le patron de
     * {@code PieceRepository.nombreReferencesHistoriques} (RM-29).
     *
     * <p>Requete native pour ne pas faire dependre le module rgpd des entites des
     * modules atelier et annexes. Toute nouvelle table referencant {@code vehicule}
     * doit s ajouter ici — en cas d oubli, le RESTRICT en base refuse quand meme la
     * suppression, et la transaction echoue plutot que de laisser passer une
     * incoherence.</p>
     */
    @Query(value = """
            SELECT (SELECT count(*) FROM rdv                 WHERE vehicule_id = :id)
                 + (SELECT count(*) FROM intervention        WHERE vehicule_id = :id)
                 + (SELECT count(*) FROM reservation_parking WHERE vehicule_id = :id)
            """, nativeQuery = true)
    long nombreReferencesHistoriques(@Param("id") Long id);

    /**
     * Suppression physique d un vehicule qu aucun historique ne reference.
     *
     * <p>Native et non {@code delete(entite)} : l entite peut avoir ete chargee alors
     * qu elle est supprimee logiquement, et le {@code @SQLRestriction} rend le
     * comportement du chemin JPA moins previsible que ce {@code DELETE} explicite.
     * Pour un vehicule que rien ne reference, la suppression definitive prend la
     * lettre du CdC au pied de la lettre : il n y a aucun historique a proteger.</p>
     */
    @Modifying
    @Query(value = "DELETE FROM vehicule WHERE id = :id", nativeQuery = true)
    void supprimerPhysiquement(@Param("id") Long id);
}
