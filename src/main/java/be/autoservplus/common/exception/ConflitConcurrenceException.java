package be.autoservplus.common.exception;

/**
 * Conflit de verrouillage optimiste lors d une transition concurrente : deux
 * administrateurs ont agi sur la meme ressource entre le chargement et le flush,
 * la seconde ecriture voit une version obsolete.
 *
 * <p>Distincte de {@link RegleMetierException} : ce n est pas une regle du cahier
 * des charges qui est violee, mais un conflit technique. Pas de code {@code RM-XX}.
 * L appelant doit inviter l utilisateur a recharger la page.</p>
 */
public class ConflitConcurrenceException extends RuntimeException {

    public ConflitConcurrenceException(String message) {
        super(message);
    }
}
