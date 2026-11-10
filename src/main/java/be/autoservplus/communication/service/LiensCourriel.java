package be.autoservplus.communication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Liens absolus des courriels, construits sur {@code autoservplus.url-publique}.
 *
 * <p>Un lien relatif n'a pas de sens dans une boîte de réception : le client de
 * messagerie ne sait pas à quel site il appartient. La base est la même que celle du
 * retour de paiement et de l'export d'agenda, réglée une fois par déploiement.</p>
 */
@Component
public class LiensCourriel {

    private final String base;

    public LiensCourriel(@Value("${autoservplus.url-publique}") String urlPublique) {
        // Une base terminée par « / » produirait « //inscription/... ».
        this.base = urlPublique.endsWith("/")
                ? urlPublique.substring(0, urlPublique.length() - 1)
                : urlPublique;
    }

    /** @param chemin chemin interne commençant par « / », paramètres compris */
    public String vers(String chemin) {
        return base + chemin;
    }
}
