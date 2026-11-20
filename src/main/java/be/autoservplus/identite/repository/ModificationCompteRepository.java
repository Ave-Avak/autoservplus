package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.ModificationCompte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ModificationCompteRepository extends JpaRepository<ModificationCompte, Long> {

    /**
     * Historique d'un compte, du plus récent au plus ancien. L'auteur est chargé en
     * JOIN FETCH : la fiche du back-office le nomme pour chaque ligne.
     */
    @Query("""
           select m from ModificationCompte m
           left join fetch m.auteur
           where m.utilisateur.id = :compte
           order by m.horodatage desc, m.id desc
           """)
    List<ModificationCompte> historiqueDe(@Param("compte") Long compte);

    /**
     * Anonymise l'historique d'un compte (F23). Les valeurs sont vidées. Le motif,
     * rédigé par un administrateur, peut citer la nouvelle adresse ou un numéro : il
     * est remplacé par le marqueur, et non vidé, parce que {@code ck_histo_compte_motif}
     * (V57) exige un motif sur toute ligne écrite par un administrateur. Le champ, la
     * date et l'auteur restent : ils disent qu'une modification a eu lieu, pas ce
     * qu'elle contenait. Requête native : les colonnes sont {@code updatable = false}
     * dans l'entité, qui reste immuable comme toute ligne de journal.
     */
    @Modifying
    @Query(value = """
           UPDATE historique_modification_compte
           SET valeur_avant = NULL,
               valeur_apres = NULL,
               motif = CASE WHEN motif IS NULL THEN NULL ELSE :marqueur END
           WHERE utilisateur_id = :compte
           """, nativeQuery = true)
    int effacerValeurs(@Param("compte") Long compte, @Param("marqueur") String marqueur);
}
