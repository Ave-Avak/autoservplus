package be.autoservplus.intervention.service;

import be.autoservplus.common.exception.RefusTraduit;

import java.util.UUID;

/**
 * Clôture refusée avant toute écriture : la facture d'atelier qu'elle émet (A15, RM-17)
 * exige un client (Q-H-14) et son adresse (art. 5 AR n° 1, Q-H-23).
 */
public class ClotureRefuseeException extends RefusTraduit {

    /** Compte dont l'adresse manque, pour le lien de l'écran vers sa modification. */
    private final transient UUID compteACompleter;

    private ClotureRefuseeException(String cle, String message, UUID compteACompleter) {
        super(cle, message);
        this.compteACompleter = compteACompleter;
    }

    static ClotureRefuseeException sansTitulaire() {
        return new ClotureRefuseeException("admin.intervention.erreur.sans-titulaire",
                "Aucun client n'est rattaché à l'intervention : sa facture ne peut pas être émise.", null);
    }

    static ClotureRefuseeException adresseIncomplete(UUID compte) {
        return new ClotureRefuseeException("admin.intervention.erreur.adresse-client",
                "L'adresse du client manque : une facture porte le nom et l'adresse du client.", compte);
    }

    /** Référence du compte à compléter, ou {@code null} quand le refus ne tient pas à l'adresse. */
    public UUID getCompteACompleter() {
        return compteACompleter;
    }
}
