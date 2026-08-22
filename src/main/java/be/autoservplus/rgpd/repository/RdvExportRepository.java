package be.autoservplus.rgpd.repository;

import be.autoservplus.reservation.domain.Rdv;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des rendez-vous d un membre pour l export (F22).
 *
 * <p>{@code open-in-view} est desactive : les lignes de prestation sont chargees
 * par {@code JOIN FETCH}, faute de quoi la conversion en objet de transfert — qui
 * a lieu hors session — leverait une {@code LazyInitializationException}. Le
 * {@code DISTINCT} neutralise la demultiplication du produit cartesien.
 *
 * <p><b>Le vehicule n est volontairement pas joint.</b> Il porte un
 * {@code @SQLRestriction} sur la suppression logique : une jointure interne aurait
 * fait disparaitre de l export tous les rendez-vous d un vehicule que le membre a
 * retire de son parc, alors que ces rendez-vous restent detenus — la suppression
 * d un vehicule est logique justement pour preserver son historique. La plaque est
 * donc rapprochee par le service depuis la liste complete des vehicules, celle qui
 * inclut les supprimes.
 *
 * <p>Le poste d atelier n est pas charge non plus : il ne figure pas dans l export
 * (ressource d organisation du garage, pas donnee de la personne).
 */
public interface RdvExportRepository extends Repository<Rdv, Long> {

    @Query("""
            SELECT DISTINCT r FROM Rdv r
            LEFT JOIN FETCH r.lignes l LEFT JOIN FETCH l.prestation
            WHERE LOWER(r.membre.email) = LOWER(:email)
            ORDER BY r.debut
            """)
    List<Rdv> pourMembre(@Param("email") String email);
}
