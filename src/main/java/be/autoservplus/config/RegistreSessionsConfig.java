package be.autoservplus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Registre des sessions ouvertes, pour que la suspension d un compte puisse couper
 * ses sessions en cours (CdC 5.2.3).
 *
 * <p><b>Le manque qu il comble.</b> {@code UtilisateurDetailsService} pose
 * {@code disabled(statut != ACTIF)}, mais cette valeur est lue dans
 * {@code loadUserByUsername}, donc <b>a l authentification seulement</b> : un compte
 * suspendu pendant qu il navigue gardait son acces jusqu a expiration de sa session.
 * La suspension etait immediate sur toute NOUVELLE connexion, et differee sur celles
 * deja ouvertes — ce qui est exactement l inverse de ce qu on attend d une mesure
 * prise en urgence.</p>
 *
 * <p><b>Le publieur d evenements n est pas un detail.</b> Sans
 * {@link HttpSessionEventPublisher}, le registre n apprend jamais qu une session
 * expire ou se ferme : il accumule des entrees mortes, et la revocation viserait des
 * sessions qui n existent plus pendant que de vraies lui echapperaient. Le conteneur
 * de servlets ne previent Spring Security que si ce bean est declare.</p>
 *
 * <p><b>Limite assumee, a savoir enoncer.</b> {@link SessionRegistryImpl} vit en
 * memoire de l instance : les sessions sont perdues au redemarrage, et ne sont pas
 * partagees entre instances. Le redemarrage n est pas un probleme — il invalide de
 * toute facon les sessions, qui vivent dans la meme memoire. Le partage en serait un
 * le jour d une montee en charge horizontale, ou une suspension ne couperait que les
 * sessions portees par l instance qui la traite ; {@code docker-compose.prod.yml} n en
 * lance qu une. Meme famille que les compteurs de debit du lot robustesse, et meme
 * correctif eventuel : un magasin partage.</p>
 */
@Configuration
public class RegistreSessionsConfig {

    @Bean
    public SessionRegistry registreSessions() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher publieurEvenementsSession() {
        return new HttpSessionEventPublisher();
    }
}
