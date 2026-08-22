package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Contrainte de la reservation (F16) : une prestation desactivee ne se reserve
 * plus — pendant de la contrainte F13 cote panier. Anciennement etiquetee RM-28 a
 * tort : cette regle ne couvre que le masquage catalogue et la conservation dans
 * l historique, pas le refus de reservation. Pas de code RM, donc pas de prefixe
 * dans le message montre au membre.
 */
public class PrestationIndisponibleException extends RegleMetierException {

    public PrestationIndisponibleException(String libelle) {
        super("La prestation %s n est plus proposee.".formatted(libelle));
    }
}
