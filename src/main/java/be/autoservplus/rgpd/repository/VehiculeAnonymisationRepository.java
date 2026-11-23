package be.autoservplus.rgpd.repository;

import be.autoservplus.reservation.domain.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Accès au parc d'un membre pour la suppression de compte (F23).
 *
 * <p>Repository propre au module rgpd plutôt qu'une méthode ajoutée à
 * {@code VehiculeRepository}, même raison que pour {@link VehiculeExportRepository} :
 * le périmètre n'est pas celui du parc utilisable. {@code findByMembre} ne rend que
 * les véhicules actifs et non supprimés. L'anonymisation doit atteindre <b>tous</b>
 * ceux qui portent encore une plaque, y compris ceux que le membre avait retirés.
 * Un véhicule supprimé logiquement garde sa plaque en base : le laisser intact
 * viderait le compte sans vider la donnée.</p>
 *
 * <p><b>Requêtes natives</b> : le {@code @SQLRestriction} de l'entité ajoute
 * {@code deleted_at IS NULL} à toute requête HQL et ne se désactive pas à la demande.
 * Le SQL natif écrit son propre filtre, et le changement reste confiné à ce
 * fichier.</p>
 */
public interface VehiculeAnonymisationRepository extends JpaRepository<Vehicule, Long> {

    /**
     * Tous les véhicules du membre, suppressions logiques comprises, triés pour un
     * traitement déterministe.
     */
    @Query(value = """
            SELECT v.* FROM vehicule v
            WHERE v.membre_id = :membreId
            ORDER BY v.id
            """, nativeQuery = true)
    List<Vehicule> tousLesVehicules(@Param("membreId") Long membreId);

    /**
     * Nombre de lignes d'historique référençant le véhicule. Miroir applicatif exact
     * des trois FK {@code ON DELETE RESTRICT} qui pointent {@code vehicule}
     * ({@code fk_rdv_vehicule}, {@code fk_intervention_vehicule},
     * {@code fk_resa_parking_vehicule}), sur le patron de
     * {@code PieceRepository.nombreReferencesHistoriques} (RM-29).
     *
     * <p>Requête native pour ne pas faire dépendre le module rgpd des entités des
     * modules atelier et annexes. Toute nouvelle table référençant {@code vehicule}
     * doit s'ajouter ici : en cas d'oubli, le RESTRICT en base refuse quand même la
     * suppression, et la transaction échoue plutôt que de laisser passer une
     * incohérence.</p>
     */
    @Query(value = """
            SELECT (SELECT count(*) FROM rdv                 WHERE vehicule_id = :id)
                 + (SELECT count(*) FROM intervention        WHERE vehicule_id = :id)
                 + (SELECT count(*) FROM reservation_parking WHERE vehicule_id = :id)
            """, nativeQuery = true)
    long nombreReferencesHistoriques(@Param("id") Long id);

    /**
     * Suppression physique d'un véhicule qu'aucun historique ne référence.
     *
     * <p>Native et non {@code delete(entite)} : l'entité peut avoir été chargée alors
     * qu'elle est supprimée logiquement, et le {@code @SQLRestriction} rend le
     * comportement du chemin JPA moins prévisible que ce {@code DELETE} explicite.
     * Pour un véhicule que rien ne référence, la suppression définitive prend la
     * lettre du CdC au pied de la lettre : il n'y a aucun historique à protéger.</p>
     */
    @Modifying
    @Query(value = "DELETE FROM vehicule WHERE id = :id", nativeQuery = true)
    void supprimerPhysiquement(@Param("id") Long id);

    /**
     * Vide la note interne de l'atelier (A16) sur toutes les interventions des
     * véhicules du membre, véhicules et interventions supprimés logiquement compris.
     *
     * <p>L'intervention reste, avec ses lignes et ses montants, que la comptabilité
     * conserve. La note, texte libre, a pu nommer la personne ou décrire ses habitudes :
     * elle part avec le compte. Le rattachement par le véhicule est celui de l'export,
     * qui la restitue.</p>
     *
     * @return le nombre d'interventions dont la note a été effacée
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE intervention SET note_interne = NULL
            WHERE note_interne IS NOT NULL
              AND vehicule_id IN (SELECT id FROM vehicule WHERE membre_id = :membreId)
            """, nativeQuery = true)
    int effacerNotesInternes(@Param("membreId") Long membreId);
}
