package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;

/**
 * Contrat du prestataire de paiement en ligne.
 *
 * <p>Le code metier depend de cette interface, jamais de Mollie : c est le meme
 * decoupage que {@code ServiceCourriel}, et l application de la strategie
 * securite §11 — chaque service tiers est isole derriere une passerelle unique.
 * L implementation active est choisie par le profil Spring : bouchon programmable
 * en developpement et en test, {@code MollieGateway} en production.</p>
 *
 * <p>Les signatures sont volontairement independantes du vocabulaire Mollie
 * (records de demande et de resultat, {@link StatutPaiement} projete) : changer
 * de prestataire ne toucherait que la passerelle.</p>
 */
public interface PrestatairePaiement {

    /**
     * Cree le paiement chez le prestataire et retourne sa reference ainsi que
     * l URL vers laquelle rediriger le membre pour payer.
     */
    PaiementCree creerPaiement(DemandePaiement demande);

    /**
     * Statut AUTHENTIQUE du paiement, relu chez le prestataire. C est l unique
     * source de verite du webhook (strategie securite §11) : le payload entrant
     * n est jamais cru, seul ce rappel fait foi.
     */
    StatutPaiement lireStatut(String referencePrestataire);
}
