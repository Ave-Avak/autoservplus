package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * <b>RM-07</b> : une demande de rendez-vous porte sur au moins une prestation.
 * Double defense derriere la validation {@code @NotEmpty} du formulaire — le
 * service ne fait pas confiance a la couche web.
 */
public class AucunePrestationChoisieException extends RegleMetierException {

    public AucunePrestationChoisieException() {
        super("Choisissez au moins une prestation.");
    }
}
