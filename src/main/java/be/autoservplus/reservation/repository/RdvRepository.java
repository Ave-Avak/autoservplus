package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RdvRepository extends JpaRepository<Rdv, Long> {

    @Query("""
            SELECT DISTINCT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            LEFT JOIN FETCH r.lignes l LEFT JOIN FETCH l.prestation
            WHERE r.reference = :reference
            """)
    Optional<Rdv> findByReference(@Param("reference") UUID reference);

    @Query("""
            SELECT DISTINCT r FROM Rdv r
            JOIN FETCH r.vehicule JOIN FETCH r.poste
            LEFT JOIN FETCH r.lignes l LEFT JOIN FETCH l.prestation
            WHERE r.membre.email = :email
            ORDER BY r.debut DESC
            """)
    List<Rdv> findByMembre(@Param("email") String email);
    /**
     * Rendez-vous actifs chevauchant la fenetre [debut, fin), tous postes confondus.
     * Sert au calcul des disponibilites : une seule requete pour toute la fenetre
     * affichee, le filtrage par poste se faisant ensuite en memoire.
     */
    @Query("""
            SELECT r FROM Rdv r JOIN FETCH r.poste
            WHERE r.statut IN :statutsActifs
              AND r.debut < :fin AND r.fin > :debut
            ORDER BY r.debut
            """)
    List<Rdv> actifsChevauchant(@Param("debut") Instant debut,
                                @Param("fin") Instant fin,
                                @Param("statutsActifs") Collection<StatutRdv> statutsActifs);

    long countByMembreEmailAndStatut(String email, StatutRdv statut);

    @Query("""
            SELECT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            WHERE r.statut = :statut
            ORDER BY r.debut
            """)
    List<Rdv> findByStatutOrderByDebut(@Param("statut") StatutRdv statut);

    /**
     * Rendez-vous d un statut donne dont le debut est atteint ou passe (debut <=
     * maintenant). Sert au tableau de bord admin pour identifier les rendez-vous
     * CONFIRME en cours ou passes qui restent a marquer (honore ou absent).
     *
     * <p>Le predicat est {@code debut <= maintenant} et non {@code fin <
     * maintenant} : le garage marque le client honore des qu il se presente au
     * debut du creneau, pas apres la fin. Le temps reel du travail est capture
     * separement par {@code date_debut_reelle} / {@code date_fin_reelle} de
     * l Intervention creee au marquage.</p>
     */
    @Query("""
            SELECT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            WHERE r.statut = :statut AND r.debut <= :maintenant
            ORDER BY r.debut
            """)
    List<Rdv> findATraiter(
            @Param("statut") StatutRdv statut,
            @Param("maintenant") Instant maintenant);
}