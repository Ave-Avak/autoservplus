package be.autoservplus.identite.web;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Page où arrive une connexion aboutie : le tableau de bord pour le back-office,
 * « Mon compte » pour le membre.
 *
 * <p>Calculée à travers la hiérarchie des rôles : un super-administrateur ne
 * porte que {@code ROLE_SUPER_ADMINISTRATEUR}, et un test direct de
 * {@code ROLE_ADMINISTRATEUR} sur ses autorités l'enverrait chez le membre.</p>
 *
 * <p>Une seule source pour les six fins de parcours, qui écrivaient chacune
 * {@code /mon-compte} en dur : mot de passe seul, second facteur, changement imposé,
 * enrôlement suivi des codes, étape rouverte une fois connecté.</p>
 */
@Component
public class PageArrivee {

    public static final String BACK_OFFICE = "/admin";
    public static final String MEMBRE = "/mon-compte";

    private static final String ROLE_BACK_OFFICE = "ROLE_ADMINISTRATEUR";

    private final RoleHierarchy hierarchie;

    public PageArrivee(RoleHierarchy hierarchie) {
        this.hierarchie = hierarchie;
    }

    public String de(Authentication authentification) {
        boolean backOffice = hierarchie
                .getReachableGrantedAuthorities(authentification.getAuthorities()).stream()
                .anyMatch(autorite -> ROLE_BACK_OFFICE.equals(autorite.getAuthority()));
        return backOffice ? BACK_OFFICE : MEMBRE;
    }
}
