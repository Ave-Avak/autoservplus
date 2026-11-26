package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RefusTraduit;

import java.util.UUID;

/**
 * La page de paiement simulé rouverte, par le bouton Retour du navigateur, sur une
 * tentative que le prestataire a déjà reçue. Rejouer une issue reprogrammerait le bouchon
 * après coup : un échec suivi d'un succès finissait en erreur 500, un succès suivi d'un
 * échec désaccordait le bouchon de la base. Le refus ramène le membre à sa commande.
 */
public class PaiementDejaTraiteException extends RefusTraduit {

    private final transient UUID referenceCommande;

    public PaiementDejaTraiteException(UUID referenceCommande) {
        super("paiement.simule.deja-traite", "Ce paiement a déjà reçu une issue.");
        this.referenceCommande = referenceCommande;
    }

    public UUID getReferenceCommande() {
        return referenceCommande;
    }
}
