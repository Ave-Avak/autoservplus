package be.autoservplus.identite.web;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Connexion commencée mais pas aboutie : le mot de passe est vérifié, une étape reste
 * (second facteur, changement imposé, enrôlement).
 *
 * <p><b>Aucune autorité.</b> Ni {@code ROLE_ADMINISTRATEUR} ni {@code ROLE_MEMBRE} : les
 * gardes {@code hasRole} de l'URL comme des méthodes refusent d'elles-mêmes, sans qu'il
 * faille les réécrire. Reste {@code authenticated()}, que ce jeton satisferait, d'où
 * {@code AuthentificationAboutie}, qui remplace {@code anyRequest().authenticated()}.</p>
 *
 * <p>L'authentification complète est conservée ici, et non reconstruite à la fin : c'est
 * elle qui porte les autorités que Spring Security a calculées au mot de passe, et la
 * restaurer telle quelle évite de dupliquer {@code UtilisateurDetailsService}.</p>
 *
 * <p><b>Spring Security 6.5 n'a pas de MFA natif</b>. La version 7 en apporte un : chaque
 * facteur y devient une autorité {@code FactorGrantedAuthority}, et l'autorisation exige
 * les facteurs voulus. Ce jeton est l'équivalent d'une authentification qui porterait le
 * facteur mot de passe sans le second. Le passage à ce mécanisme a été écarté en V1 : il
 * suppose Spring Boot 4, et TOTP n'y est pas un facteur fourni, il faudrait encore lui
 * écrire sa propre autorité.</p>
 *
 * <p>Le jeton porte l'instant où son étape s'est ouverte : {@link ParcoursConnexion}
 * l'abandonne au-delà de cinq minutes. Sans cette date, une connexion partielle durait
 * autant que la session, et un mot de passe juste laissait l'étape ouverte tant que la
 * session était entretenue.</p>
 */
public class AuthentificationPartielle extends AbstractAuthenticationToken {

    private final Authentication complete;
    private final boolean secondFacteurValide;
    private final Instant etapeOuverteLe;

    public AuthentificationPartielle(Authentication complete, boolean secondFacteurValide,
                                     Instant etapeOuverteLe) {
        super(List.of());
        this.complete = Objects.requireNonNull(complete, "complete");
        this.secondFacteurValide = secondFacteurValide;
        this.etapeOuverteLe = Objects.requireNonNull(etapeOuverteLe, "etapeOuverteLe");
        // Authentifié au sens du filtre d'exception : un refus d'accès mène alors au
        // gestionnaire de refus, qui renvoie vers l'étape, et non à la page de connexion,
        // ce qui ferait recommencer le mot de passe à chaque clic.
        setAuthenticated(true);
    }

    /**
     * Vrai pour une connexion aboutie : ni absente, ni anonyme, ni partielle. Les
     * compteurs de l'en-tête s'y tiennent : un jeton partiel satisfait
     * {@code isAuthenticated()}, et ses comptages s'afficheraient avant la fin de la
     * connexion.
     */
    public static boolean aboutie(Authentication authentification) {
        return authentification != null
                && authentification.isAuthenticated()
                && !(authentification instanceof AnonymousAuthenticationToken)
                && !(authentification instanceof AuthentificationPartielle);
    }

    public AuthentificationPartielle avecSecondFacteurValide() {
        return new AuthentificationPartielle(complete, true, etapeOuverteLe);
    }

    /** Le même jeton, pour l'étape qui s'ouvre à cet instant. */
    public AuthentificationPartielle etapeOuverteA(Instant instant) {
        return new AuthentificationPartielle(complete, secondFacteurValide, instant);
    }

    public Instant etapeOuverteLe() {
        return etapeOuverteLe;
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
                && secondFacteurValide == partielle.secondFacteurValide
                && etapeOuverteLe.equals(partielle.etapeOuverteLe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(complete, secondFacteurValide, etapeOuverteLe);
    }
}
