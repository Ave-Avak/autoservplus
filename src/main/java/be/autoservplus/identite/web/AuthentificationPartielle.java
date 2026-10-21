package be.autoservplus.identite.web;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Objects;

/**
 * Connexion commencee mais pas aboutie : le mot de passe est verifie, une etape reste
 * (second facteur, changement impose, enrolement).
 *
 * <p><b>Aucune autorite.</b> Ni {@code ROLE_ADMINISTRATEUR} ni {@code ROLE_MEMBRE} : les
 * gardes {@code hasRole} de l URL comme des methodes refusent d elles-memes, sans qu il
 * faille les reecrire. Reste {@code authenticated()}, que ce jeton satisferait — d ou
 * {@code AuthentificationAboutie}, qui remplace {@code anyRequest().authenticated()}.</p>
 *
 * <p>L authentification complete est conservee ici, et non reconstruite a la fin : c est
 * elle qui porte les autorites que Spring Security a calculees au mot de passe, et la
 * restaurer telle quelle evite de dupliquer {@code UtilisateurDetailsService}.</p>
 *
 * <p><b>Spring Security 6.5 n a pas de MFA natif</b> ; la version 7 en apporte un, sous
 * forme d autorites de facteur (voir le registre). Ce jeton est l equivalent d une
 * authentification qui porterait le facteur mot de passe sans le second.</p>
 */
public class AuthentificationPartielle extends AbstractAuthenticationToken {

    private final Authentication complete;
    private final boolean secondFacteurValide;

    public AuthentificationPartielle(Authentication complete, boolean secondFacteurValide) {
        super(List.of());
        this.complete = Objects.requireNonNull(complete, "complete");
        this.secondFacteurValide = secondFacteurValide;
        // Authentifie au sens du filtre d exception : un refus d acces mene alors au
        // gestionnaire de refus, qui renvoie vers l etape, et non a la page de connexion
        // — ce qui ferait recommencer le mot de passe a chaque clic.
        setAuthenticated(true);
    }

    /**
     * Vrai pour une connexion aboutie : ni absente, ni anonyme, ni partielle. Les
     * compteurs de l en-tete s y tiennent — un jeton partiel satisfait
     * {@code isAuthenticated()}, et ses comptages s afficheraient avant la fin de la
     * connexion.
     */
    public static boolean aboutie(Authentication authentification) {
        return authentification != null
                && authentification.isAuthenticated()
                && !(authentification instanceof AnonymousAuthenticationToken)
                && !(authentification instanceof AuthentificationPartielle);
    }

    public AuthentificationPartielle avecSecondFacteurValide() {
        return new AuthentificationPartielle(complete, true);
    }

    public Authentication complete() {
        return complete;
    }

    public boolean secondFacteurValide() {
        return secondFacteurValide;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return complete.getPrincipal();
    }

    @Override
    public String getName() {
        return complete.getName();
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof AuthentificationPartielle partielle
                && complete.equals(partielle.complete)
                && secondFacteurValide == partielle.secondFacteurValide;
    }

    @Override
    public int hashCode() {
        return Objects.hash(complete, secondFacteurValide);
    }
}
