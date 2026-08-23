package be.autoservplus.journal.repository;

import be.autoservplus.journal.service.dto.EntreeJournal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecture du journal d audit (BL-7).
 *
 * <p><b>UNION native plutot que deux requetes fusionnees en memoire.</b> Les deux
 * tables historisees n ont ni les memes colonnes ni la meme entite racine ; les lire
 * separement obligerait a charger l integralite de chacune avant de trier et de
 * tronquer, ce qui rend le cout du filtre proportionnel a tout l historique. En SQL,
 * le tri et la limite s appliquent au resultat fusionne.</p>
 *
 * <p><b>Aucune ecriture, aucun {@code @Modifying}.</b> Un journal d audit qui pourrait
 * etre modifie depuis l application ne prouverait plus rien : la seule facon d y
 * ajouter une ligne reste la transaction metier qui l a produite.</p>
 */
@Repository
public class JournalRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Entrees du journal, de la plus recente a la plus ancienne.
     *
     * <p>Les trois filtres sont facultatifs et se combinent. {@code acteur} est
     * rapproche du nom et du prenom, jamais de l adresse de courriel : le garage
     * cherche « Dupont », et exposer un filtre par adresse ferait du journal un moyen
     * de retrouver des courriels.</p>
     *
     * <p>Les parametres sont <b>castes explicitement</b> : un filtre absent est lie a
     * {@code NULL}, et PostgreSQL refuse alors d inferer le type
     * (« could not determine data type of parameter »). Le cast rend le filtre
     * facultatif sans avoir a construire une variante de requete par combinaison.</p>
     *
     * @param type   {@code CATALOGUE}, {@code INTERVENTION}, ou {@code null} pour tout
     * @param limite plafond de lignes rendues
     */
    public List<Object[]> rechercher(String type, String acteur, Instant depuis, Instant jusqua,
                                     int limite) {
        boolean tousTypes = type == null || type.isBlank();
        boolean catalogue = tousTypes || EntreeJournal.TYPE_CATALOGUE.equals(type);
        boolean intervention = tousTypes || EntreeJournal.TYPE_INTERVENTION.equals(type);

        List<String> morceaux = new ArrayList<>();
        if (catalogue) {
            morceaux.add("""
                    SELECT h.horodatage AS horodatage,
                           'CATALOGUE' AS type,
                           u.prenom AS prenom, u.nom AS nom,
                           h.type_entite AS cible,
                           h.champ_modifie AS champ,
                           h.valeur_avant AS avant,
                           h.valeur_apres AS apres,
                           NULL AS motif
                    FROM historique_modification_catalogue h
                    LEFT JOIN utilisateur u ON u.id = h.auteur_id
                    WHERE (CAST(:depuis AS timestamptz) IS NULL OR h.horodatage >= CAST(:depuis AS timestamptz))
                      AND (CAST(:jusqua AS timestamptz) IS NULL OR h.horodatage < CAST(:jusqua AS timestamptz))
                      AND (CAST(:acteur AS text) IS NULL
                           OR u.nom ILIKE CAST(:motif AS text)
                           OR u.prenom ILIKE CAST(:motif AS text))
                    """);
        }
        if (intervention) {
            morceaux.add("""
                    SELECT h.horodatage AS horodatage,
                           'INTERVENTION' AS type,
                           u.prenom AS prenom, u.nom AS nom,
                           i.numero AS cible,
                           NULL AS champ,
                           h.statut_avant AS avant,
                           h.statut_apres AS apres,
                           h.motif AS motif
                    FROM historique_statut_intervention h
                    JOIN intervention i ON i.id = h.intervention_id
                    LEFT JOIN utilisateur u ON u.id = h.auteur_id
                    WHERE (CAST(:depuis AS timestamptz) IS NULL OR h.horodatage >= CAST(:depuis AS timestamptz))
                      AND (CAST(:jusqua AS timestamptz) IS NULL OR h.horodatage < CAST(:jusqua AS timestamptz))
                      AND (CAST(:acteur AS text) IS NULL
                           OR u.nom ILIKE CAST(:motif AS text)
                           OR u.prenom ILIKE CAST(:motif AS text))
                    """);
        }
        if (morceaux.isEmpty()) {
            return List.of();
        }

        var requete = em.createNativeQuery(
                String.join(" UNION ALL ", morceaux) + " ORDER BY horodatage DESC");
        requete.setParameter("depuis", depuis);
        requete.setParameter("jusqua", jusqua);
        requete.setParameter("acteur", acteur == null || acteur.isBlank() ? null : acteur);
        requete.setParameter("motif", acteur == null || acteur.isBlank() ? null : "%" + acteur + "%");
        requete.setMaxResults(limite);

        @SuppressWarnings("unchecked")
        List<Object[]> lignes = requete.getResultList();
        return lignes;
    }
}
