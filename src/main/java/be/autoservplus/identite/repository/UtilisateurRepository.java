package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acces aux comptes de la plateforme.
 *
 * <p>Spring Data construit les requetes a partir du nom des methodes. Le filtre sur la
 * suppression logique est applique automatiquement par l annotation SQLRestriction posee
 * sur l entite : aucune methode n a besoin de le repeter.</p>
 */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    Optional<Utilisateur> findByReference(UUID reference);

    Optional<Utilisateur> findByJetonVerification(String jeton);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Utilisateur> findByJetonChangementEmail(String jeton);

    /**
     * Une demande de changement vise-t-elle deja cette adresse ?
     *
     * <p>Distinct de {@link #existsByEmailIgnoreCase(String)} : une adresse peut
     * n appartenir a personne et etre pourtant reservee par une demande en cours. Les
     * deux controles sont necessaires, et l index partiel
     * {@code uq_utilisateur_email_en_attente} reprend celui-ci en base.</p>
     */
    boolean existsByEmailEnAttenteIgnoreCase(String emailEnAttente);

    List<Utilisateur> findByTypeUtilisateurAndStatut(TypeUtilisateur type, StatutUtilisateur statut);

    long countByStatut(StatutUtilisateur statut);

    /** Comptes du back-office, pour l ecran de gestion des administrateurs. */
    List<Utilisateur> findByTypeUtilisateurInOrderByNomAscPrenomAsc(
            Collection<TypeUtilisateur> types);

    /**
     * Combien de super-administrateurs ACTIFS, en excluant un compte donne ?
     *
     * <p>Sert l invariant  il doit rester un super-administrateur actif  : la question
     * posee avant de suspendre est  combien en resterait-il ? , donc le compte vise
     * doit sortir du decompte. L exclusion est faite ICI plutot que par une
     * soustraction cote service — soustraire un supposerait que le compte vise est
     * lui-meme actif, ce qui est vrai a la suspension et faux partout ailleurs.</p>
     */
    @Query("""
           select count(u) from Utilisateur u
           where u.typeUtilisateur = be.autoservplus.identite.domain.TypeUtilisateur.SUPER_ADMINISTRATEUR
             and u.statut = be.autoservplus.identite.domain.StatutUtilisateur.ACTIF
             and u.id <> :exclu
           """)
    long compterSuperAdministrateursActifsSauf(@Param("exclu") Long exclu);

    /**
     * Recherche des membres par nom, prenom ou adresse, pour l ecran de gestion.
     *
     * <p><b>Le motif est un MOTIF, jamais un terme, et jamais nul.</b> La version
     * precedente testait {@code :filtre is null} pour rendre tout le monde quand rien
     * n etait cherche : PostgreSQL ne sait pas inferer le type d un parametre nul et
     * echouait sur {@code function lower(bytea) does not exist} — l ecran repondait
     * 500 des son ouverture. Meme piege que celui deja consigne pour le journal
     * d audit (BL-7).
     *
     * <p>Plutot que de caster le parametre, l appelant compose le motif et passe
     * {@code %} quand il n y a pas de recherche : la requete n a plus de branche
     * nulle du tout, donc plus rien a inferer. Une condition en moins vaut mieux
     * qu une condition rendue correcte.</p>
     *
     * @param motif motif SQL deja borne par des {@code %}, en minuscules
     */
    @Query("""
           select u from Utilisateur u
           where u.typeUtilisateur = be.autoservplus.identite.domain.TypeUtilisateur.MEMBRE
             and (lower(u.nom) like :motif
                  or lower(u.prenom) like :motif
                  or lower(u.email) like :motif)
           order by u.nom asc, u.prenom asc
           """)
    List<Utilisateur> rechercherMembres(@Param("motif") String motif);
}