package be.autoservplus.rgpd.repository;

import be.autoservplus.reservation.domain.Rdv;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des rendez-vous d un membre pour l export (F22).
 *
 * <p>{@code open-in-view} est desactive : le vehicule et les lignes de prestation
 * sont charges par {@code JOIN FETCH} dans la requete, faute de quoi la conversion
 * en objet de transfert — qui a lieu hors session — leverait une
 * {@code LazyInitializationException}. Le {@code DISTINCT} neutralise la
 * demultiplication des lignes du produit cartesien.
 *
 * <p>Le poste d atelier n est volontairement pas charge : il ne figure pas dans
 * l export (ressource d organisation du garage, pas donnee de la personne).
 */
public interface RdvExportRepository extends Repository<Rdv, Long> {

    @Query("""
            SELECT DISTINCT r FROM Rdv r
            JOIN FETCH r.vehicule
            LEFT JOIN FETCH r.lignes l LEFT JOIN FETCH l.prestation
            WHERE LOWER(r.membre.email) = LOWER(:email)
            ORDER BY r.debut
            """)
    List<Rdv> pourMembre(@Param("email") String email);
}
