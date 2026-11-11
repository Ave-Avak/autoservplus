package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Rendez-vous confirmés dont le rappel est dû : début dans l'avenir, au plus tard à
     * {@code limite}, rappel pas encore réservé.
     */
    @Query("""
            SELECT r FROM Rdv r JOIN FETCH r.membre
            WHERE r.statut = be.autoservplus.reservation.domain.StatutRdv.CONFIRME
              AND r.rappelEnvoyeLe IS NULL
              AND r.debut > :maintenant AND r.debut <= :limite
            ORDER BY r.debut
            """)
    List<Rdv> rappelsDus(@Param("maintenant") Instant maintenant, @Param("limite") Instant limite);

    /**
     * Réserve le rappel avant de l'envoyer. Rend 1 au seul passage qui l'obtient : un
     * second passage, concurrent ou repris, lit la colonne déjà remplie et rend 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE rdv SET rappel_envoye_le = :maintenant
            WHERE id = :id AND rappel_envoye_le IS NULL AND statut = 'CONFIRME' AND deleted_at IS NULL
            """, nativeQuery = true)
    int reserverRappel(@Param("id") Long id, @Param("maintenant") Instant maintenant);

    /** Demandes en attente dont l'échéance de réponse est atteinte. */
    @Query("""
            SELECT r FROM Rdv r JOIN FETCH r.membre
            WHERE r.statut = be.autoservplus.reservation.domain.StatutRdv.EN_ATTENTE
              AND r.echeanceReponse IS NOT NULL AND r.echeanceReponse <= :maintenant
            ORDER BY r.echeanceReponse
            """)
    List<Rdv> demandesEchues(@Param("maintenant") Instant maintenant);

    /**
     * Demandes en attente arrivées à mi-chemin entre leur création et leur échéance,
     * échéance non atteinte et relance pas encore réservée.
     */
    @Query(value = """
            SELECT * FROM rdv
            WHERE deleted_at IS NULL AND statut = 'EN_ATTENTE'
              AND echeance_reponse IS NOT NULL AND relance_envoyee_le IS NULL
              AND echeance_reponse > :maintenant
              AND created_at + (echeance_reponse - created_at) / 2 <= :maintenant
            ORDER BY echeance_reponse
            """, nativeQuery = true)
    List<Rdv> relancesDues(@Param("maintenant") Instant maintenant);

    /** Réserve la relance au garage, sur le patron de {@link #reserverRappel}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE rdv SET relance_envoyee_le = :maintenant
            WHERE id = :id AND relance_envoyee_le IS NULL AND statut = 'EN_ATTENTE' AND deleted_at IS NULL
            """, nativeQuery = true)
    int reserverRelance(@Param("id") Long id, @Param("maintenant") Instant maintenant);
}
