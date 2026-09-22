package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

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
}