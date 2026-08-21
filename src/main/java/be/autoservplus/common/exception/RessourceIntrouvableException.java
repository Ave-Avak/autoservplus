package be.autoservplus.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levee lorsqu une ressource demandee n existe pas ou a ete supprimee logiquement.
 *
 * <p>Marquee {@code @ResponseStatus(NOT_FOUND)} : les endpoints qui ne la catchent
 * pas retournent naturellement un 404 plutot qu un 500. Les controleurs qui
 * veulent une redirection avec flash message peuvent continuer a la catcher
 * explicitement.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String typeRessource, Object identifiant) {
        super("%s introuvable : %s".formatted(typeRessource, identifiant));
    }
}