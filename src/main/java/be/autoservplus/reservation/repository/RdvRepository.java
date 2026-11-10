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

    /**
     * Rendez-vous d'un des statuts donnés qui ne sont pas encore terminés, avec membre,
     * poste et prestations. Sert à la liste des conflits du planning (A24), qui les
     * filtre ensuite en mémoire selon la plage, le poste ou l'indisponibilité touchés.
     */
    @Query("""
            SELECT DISTINCT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.poste
            LEFT JOIN FETCH r.lignes l LEFT JOIN FETCH l.prestation
            WHERE r.statut IN :statuts AND r.fin > :apres
            ORDER BY r.debut
            """)
    List<Rdv> nonTerminesAvecDetails(@Param("apres") Instant apres,
                                     @Param("statuts") Collection<StatutRdv> statuts);

    @Query("""
            SELECT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            WHERE r.statut = :statut
            ORDER BY r.debut
            """)
    List<Rdv> findByStatutOrderByDebut(@Param("statut") StatutRdv statut);

    /**
     * Rendez-vous d'un statut donné dont le début tombe dans [depuis, jusqua], bornes
     * comprises. Sert à la section « du jour » de l'écran de traitement : depuis minuit,
     * dans le fuseau de l'atelier, jusqu'à maintenant.
     *
     * <p>Le prédicat porte sur le début et non sur la fin : le garage marque le client
     * honoré dès qu'il se présente, pas après la fin du créneau. Le temps réel du
     * travail est capturé à part par l'intervention créée au marquage.</p>
     */
    @Query("""
            SELECT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            WHERE r.statut = :statut AND r.debut >= :depuis AND r.debut <= :jusqua
            ORDER BY r.debut
            """)
    List<Rdv> commencesEntre(@Param("statut") StatutRdv statut,
                             @Param("depuis") Instant depuis,
                             @Param("jusqua") Instant jusqua);

    /** Rendez-vous d'un statut donné commencés avant un instant, du plus ancien au plus récent. */
    @Query("""
            SELECT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            WHERE r.statut = :statut AND r.debut < :avant
            ORDER BY r.debut
            """)
    List<Rdv> commencesAvant(@Param("statut") StatutRdv statut, @Param("avant") Instant avant);

    /**
     * Agenda du back-office : tous les rendez-vous, quel que soit leur statut, dont le
     * début tombe dans [depuis, avant). Les prestations sont chargées avec, la page
     * les affiche toutes.
     */
    @Query("""
            SELECT DISTINCT r FROM Rdv r
            JOIN FETCH r.membre JOIN FETCH r.vehicule JOIN FETCH r.poste
            LEFT JOIN FETCH r.lignes l LEFT JOIN FETCH l.prestation
            WHERE r.debut >= :depuis AND r.debut < :avant
            ORDER BY r.debut
            """)
    List<Rdv> agenda(@Param("depuis") Instant depuis, @Param("avant") Instant avant);
}
