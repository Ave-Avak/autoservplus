package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;

/**
 * Contrat du prestataire de paiement en ligne.
 *
 * <p>Le code metier depend de cette interface, jamais de Mollie : c est le meme
 * decoupage que {@code ServiceCourriel}, et l application de la strategie
 * securite §11 — chaque service tiers est isole derriere une passerelle unique.
 * L implementation active est choisie par la PRESENCE D UN IDENTIFIANT de
 * prestataire ({@code autoservplus.paiement.mollie.cle-api}) : {@code MollieGateway}
 * des qu il y en a un, bouchon programmable sinon. Le profil Spring ne decide pas —
 * il l a fait, et deployer sans identifiant levait alors une exception au moment de
 * payer.</p>
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
     * Etat AUTHENTIQUE du paiement, relu chez le prestataire : statut, et moyen de
     * paiement s il est deja connu. C est l unique source de verite du webhook
     * (strategie securite §11) : le payload entrant n est jamais cru, seul ce rappel
     * fait foi.
     *
     * <p>Le moyen voyage avec le statut parce qu il arrive dans la meme reponse. Un
     * accesseur separe imposerait un second appel reseau pour une donnee deja
     * recue.</p>
     */
    EtatPaiement lireEtat(String referencePrestataire);

    /**
     * Rembourse un paiement encaisse (F30, RM-23) et retourne l identifiant du
     * mouvement chez le prestataire.
     *
     * <p>Un remboursement est un mouvement <b>distinct</b> de l encaissement, pas son
     * annulation : le paiement d origine reste au dossier, la facture qui l atteste
     * est immuable, et c est une note de credit qui les contre-passe dans les livres.
     * D ou une methode a part entiere plutot qu un {@code annuler(...)} — le
     * vocabulaire de la passerelle doit dire ce qui se passe reellement.</p>
     *
     * <p>La cle d idempotence portee par la demande est <b>derivee du paiement</b>,
     * donc stable d un appel a l autre : c est ce qui empeche un rejeu de rembourser
     * deux fois, la ou une cle tiree au hasard offrirait la garantie inverse.</p>
     */
    RemboursementCree rembourser(DemandeRemboursement demande);
}
