package be.autoservplus.avis.repository;

import be.autoservplus.avis.domain.Avis;
import be.autoservplus.avis.service.dto.NotePrestation;
import be.autoservplus.avis.service.dto.SyntheseAvis;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvisRepository extends JpaRepository<Avis, Long> {

    Optional<Avis> findByReference(UUID reference);

    boolean existsByIntervention(Intervention intervention);

    /** Avis du membre, pour son propre suivi et pour l export RGPD. */
    List<Avis> findByMembreOrderByDateDepotDesc(Utilisateur membre);

    /**
     * Avis publies portant sur une prestation, via les lignes de l intervention notee.
     *
     * <p>La jointure passe par {@code ligne_intervention} et non par le rendez-vous :
     * une prestation peut avoir ete ajoutee en cours de travaux (RM-14), auquel cas
     * elle figure dans les lignes sans avoir jamais ete au rendez-vous initial. Juger
     * sur le rendez-vous manquerait ces prestations la.</p>
     */
    @Query("""
            SELECT a FROM Avis a
            JOIN a.intervention i
            JOIN i.lignes l
            WHERE l.prestation.reference = :reference
              AND a.publie = true
            ORDER BY a.dateDepot DESC
            """)
    List<Avis> publiesPourPrestation(@Param("reference") UUID reference);

    /**
     * Moyenne et nombre d avis publies d une prestation, en une requete.
     *
     * <p>Expression de construction plutot qu un {@code Object[]} : le tableau
     * obligerait l appelant a caster position par position, et une inversion de
     * colonnes ne se verrait qu a l execution.</p>
     *
     * <p>{@code AVG} rend {@code null} quand aucun avis ne correspond. L appelant
     * distingue « pas encore note » de « note a zero », qui n existe pas puisque le
     * CHECK borne la note a 1.</p>
     */
    @Query("""
            SELECT new be.autoservplus.avis.service.dto.SyntheseAvis(AVG(a.note), COUNT(a))
            FROM Avis a
            JOIN a.intervention i
            JOIN i.lignes l
            WHERE l.prestation.reference = :reference
              AND a.publie = true
            """)
    SyntheseAvis syntheseParPrestation(@Param("reference") UUID reference);

    /**
     * Note moyenne et nombre d'avis publiés de plusieurs prestations en une requête, pour
     * les cartes de la liste : une requête par carte en ferait trente. Une prestation sans
     * avis publié n'a pas de ligne.
     *
     * <p>Le rattachement passe par une ligne de l'intervention notée, comme
     * {@link #syntheseParPrestation}, mais en {@code EXISTS} : une intervention qui porte deux
     * fois la même prestation ne compte son avis qu'une fois.</p>
     */
    @Query("""
            SELECT new be.autoservplus.avis.service.dto.NotePrestation(p.reference, AVG(a.note), COUNT(a))
            FROM Avis a, Prestation p
            WHERE a.publie = true
              AND p.reference IN :references
              AND EXISTS (SELECT l FROM LigneIntervention l
                          WHERE l.intervention = a.intervention AND l.prestation = p)
            GROUP BY p.reference
            """)
    List<NotePrestation> notesParPrestation(@Param("references") Collection<UUID> references);

    /** Moyenne et nombre de tous les avis publiés, pour l'accueil. */
    @Query("""
            SELECT new be.autoservplus.avis.service.dto.SyntheseAvis(AVG(a.note), COUNT(a))
            FROM Avis a
            WHERE a.publie = true
            """)
    SyntheseAvis syntheseGenerale();

    /**
     * Derniers avis publiés qui portent un commentaire, pour l'accueil. Une note sans texte
     * n'a rien à montrer dans une carte : elle compte dans la moyenne seulement.
     */
    @Query("""
            SELECT a FROM Avis a
            JOIN FETCH a.membre
            JOIN FETCH a.intervention
            WHERE a.publie = true AND a.commentaire IS NOT NULL AND TRIM(a.commentaire) <> ''
            ORDER BY a.dateDepot DESC
            """)
    List<Avis> derniersPubliesCommentes(Pageable page);

    /** Tous les avis, du plus recent au plus ancien, pour l ecran de moderation. */
    @Query("""
            SELECT a FROM Avis a
            JOIN FETCH a.membre
            JOIN FETCH a.intervention
            ORDER BY a.dateDepot DESC
            """)
    List<Avis> tousPourModeration();

    /**
     * Avis dont l auteur est le compte anonymise (F23). Charge par identifiant de
     * membre plutot que par entite : l appelant vient de reecrire la ligne
     * utilisateur, et repasser par elle en session Hibernate melerait deux etats.
     */
    @Query("SELECT a FROM Avis a WHERE a.membre.id = :membreId AND a.commentaire IS NOT NULL")
    List<Avis> avecCommentaireDuMembre(@Param("membreId") Long membreId);
}
