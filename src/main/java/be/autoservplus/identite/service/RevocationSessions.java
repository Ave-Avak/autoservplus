package be.autoservplus.identite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ferme les sessions ouvertes d un compte (CdC 5.2.3).
 *
 * <p><b>Pourquoi ce composant existe.</b> Ecrire {@code SUSPENDU} suffit a refuser la
 * prochaine connexion, {@code UtilisateurDetailsService} en tirant {@code disabled}.
 * Mais cette valeur est lue a l authentification : un compte suspendu pendant qu il
 * navigue gardait son acces jusqu a expiration de sa session, c est-a-dire que la
 * mesure prise en urgence etait la seule a ne pas s appliquer tout de suite.</p>
 *
 * <p><b>La cle du registre est le principal, pas l entite.</b> Spring Security y range
 * les sessions sous l objet qu il a authentifie — un {@code UserDetails} dont le nom
 * est l adresse de courriel. On cherche donc par adresse, et la comparaison est
 * insensible a la casse comme partout ailleurs dans le projet.</p>
 *
 * <p><b>Expiration plutot que suppression.</b> {@link SessionInformation#expireNow()}
 * marque la session : le filtre de Spring Security la refuse a la requete suivante et
 * redirige vers la connexion. La retirer du registre la rendrait invisible sans la
 * fermer — l utilisateur continuerait de naviguer, et plus rien ne saurait qu il le
 * fait.</p>
 */
@Component
public class RevocationSessions {

    private static final Logger JOURNAL = LoggerFactory.getLogger(RevocationSessions.class);

    private final SessionRegistry registre;

    public RevocationSessions(SessionRegistry registre) {
        this.registre = registre;
    }

    /**
     * @param email adresse du compte dont les sessions doivent tomber
     * @return le nombre de sessions fermees, pour que l appelant puisse le journaliser
     */
    public int revoquer(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        int fermees = 0;
        for (Object principal : registre.getAllPrincipals()) {
            if (!correspond(principal, email)) {
                continue;
            }
            // false : les sessions deja expirees sont incluses. Les exclure ferait
            // dependre le resultat d un menage asynchrone, donc le rendrait variable
            // d une execution a l autre ; expireNow() sur une session deja expiree est
            // sans effet.
            List<SessionInformation> sessions = registre.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
                fermees++;
            }
        }
        if (fermees > 0) {
            JOURNAL.info("{} session(s) fermee(s) a la suite d une suspension de compte.",
                    fermees);
        }
        return fermees;
    }

    private static boolean correspond(Object principal, String email) {
        String nom = principal instanceof UserDetails details
                ? details.getUsername()
                : String.valueOf(principal);
        return email.equalsIgnoreCase(nom);
    }
}
