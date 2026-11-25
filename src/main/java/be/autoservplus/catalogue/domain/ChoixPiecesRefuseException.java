package be.autoservplus.catalogue.domain;

import be.autoservplus.common.exception.RefusTraduit;

import java.util.UUID;

/**
 * Choix des pièces refusé au moment de figer une ligne : absent alors que la prestation
 * propose un prix pose seule, ou donné alors qu'elle n'en propose pas. La référence de la
 * prestation permet à l'écran d'ancrer l'erreur sous le bon groupe de boutons.
 */
public class ChoixPiecesRefuseException extends RefusTraduit {

    private final transient UUID prestation;

    private ChoixPiecesRefuseException(UUID prestation, String cle, String libelle, String message) {
        super(null, cle, new Object[]{libelle}, message);
        this.prestation = prestation;
    }

    static ChoixPiecesRefuseException manquant(Prestation prestation) {
        return new ChoixPiecesRefuseException(prestation.getReference(), "choix-pieces.erreur.manquant",
                prestation.getLibelle(),
                "Choisissez qui fournit les pièces pour « %s ».".formatted(prestation.getLibelle()));
    }

    static ChoixPiecesRefuseException nonPropose(Prestation prestation) {
        return new ChoixPiecesRefuseException(prestation.getReference(), "choix-pieces.erreur.non-propose",
                prestation.getLibelle(),
                "La prestation « %s » ne propose pas de choix des pièces.".formatted(prestation.getLibelle()));
    }

    /** Référence de la prestation en cause. */
    public UUID getPrestation() {
        return prestation;
    }
}
