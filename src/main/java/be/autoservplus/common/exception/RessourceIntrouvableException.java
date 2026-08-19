package be.autoservplus.common.exception;

/** Levee lorsqu une ressource demandee n existe pas ou a ete supprimee logiquement. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String typeRessource, Object identifiant) {
        super("%s introuvable : %s".formatted(typeRessource, identifiant));
    }
}