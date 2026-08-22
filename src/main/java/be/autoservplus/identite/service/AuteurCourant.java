package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolution de l utilisateur derriere l action en cours, pour les journaux qui
 * doivent porter une vraie cle etrangere vers {@code utilisateur}.
 *
 * <p>L identite vient <b>toujours</b> du contexte de securite, jamais d un parametre
 * de requete : un identifiant d auteur soumis par le client serait falsifiable, et un
 * journal falsifiable ne vaut rien. Memes gardes que
 * {@code JpaAuditingConfig#auditorProvider}, qui alimente {@code created_by} avec le
 * meme principal ; la difference est le niveau de detail — l audit se contente du nom,
 * un journal metier remonte a l entite pour poser la FK.</p>
 *
 * <p>Retourne {@code null} pour un traitement sans utilisateur authentifie (tache
 * planifiee, webhook de paiement) ou pour un principal sans compte en base : la trace
 * doit exister meme sans auteur identifiable, les colonnes {@code auteur_id} des
 * journaux sont nullables pour cette raison.</p>
 *
 * <p>Vit dans {@code identite}, module proprietaire de {@code Utilisateur} : les
 * modules qui journalisent (intervention, catalogue) en dependent deja, l inverse
 * serait une inversion de dependance.</p>
 */
@Component
public class AuteurCourant {

    private static final String PRINCIPAL_ANONYME = "anonymousUser";

    private final UtilisateurRepository utilisateurs;

    public AuteurCourant(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    /** @return l utilisateur authentifie, ou {@code null} s il n y en a pas */
    public Utilisateur resoudre() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || PRINCIPAL_ANONYME.equals(auth.getPrincipal())) {
            return null;
        }
        return utilisateurs.findByEmailIgnoreCase(auth.getName()).orElse(null);
    }
}
