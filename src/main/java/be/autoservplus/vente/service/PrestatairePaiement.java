package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;

/**
 * Contrat du prestataire de paiement en ligne.
 *
 * <p>Le code métier dépend de cette interface, jamais de Mollie : c'est le même
 * découpage que {@code ServiceCourriel}, et l'application de la stratégie
 * sécurité §11. Chaque service tiers est isolé derrière une passerelle unique.
 * L'implémentation active est choisie par la PRESENCE D'UN IDENTIFIANT de
 * prestataire ({@code autoservplus.paiement.mollie.cle-api}) : {@code MollieGateway}
 * dès qu'il y en a un, bouchon programmable sinon. Le profil Spring ne décide pas.
 * Il décidait autrefois, et déployer sans identifiant levait alors une exception au moment de
 * payer.</p>
 *
 * <p>Les signatures sont volontairement indépendantes du vocabulaire Mollie
 * (records de demande et de résultat, {@link StatutPaiement} projeté) : changer
 * de prestataire ne toucherait que la passerelle.</p>
 */
public interface PrestatairePaiement {

    /**
     * Crée le paiement chez le prestataire et retourne sa référence ainsi que
     * l'URL vers laquelle rediriger le membre pour payer.
     */
    PaiementCree creerPaiement(DemandePaiement demande);

    /**
     * État AUTHENTIQUE du paiement, relu chez le prestataire : statut, et moyen de
     * paiement s'il est déjà connu. C'est l'unique source de vérité du webhook
     * (stratégie sécurité §11) : le payload entrant n'est jamais cru, seul ce rappel
     * fait foi.
     *
     * <p>Le moyen voyage avec le statut parce qu'il arrive dans la même réponse. Un
     * accesseur séparé imposerait un second appel réseau pour une donnée déjà
     * reçue.</p>
     */
    EtatPaiement lireEtat(String referencePrestataire);

    /**
     * Annule chez le prestataire un paiement que personne n'a encore réglé, et rend
     * l'état qu'il en rapporte.
     *
     * <p>N'a de sens que pour un paiement lu {@link EtatPaiement#annulable()} : le job
     * RM-21 ferme ainsi la page de paiement d'une commande qu'il annule, pour qu'un
     * règlement ne puisse plus y arriver. Un paiement réglé entre la lecture et l'appel
     * est refusé par le prestataire, et ce refus remonte comme une panne
     * ({@link PrestataireIndisponibleException}) : rien n'est annulé de notre côté, et
     * la lecture suivante constate l'encaissement.</p>
     */
    EtatPaiement annuler(String referencePrestataire);

    /**
     * Rembourse un paiement encaissé (F30, RM-23) et retourne l'identifiant du
     * mouvement chez le prestataire.
     *
     * <p>Un remboursement est un mouvement <b>distinct</b> de l'encaissement, pas son
     * annulation : le paiement d'origine reste au dossier, la facture qui l'atteste
     * est immuable, et c'est une note de crédit qui les contre-passe dans les livres.
     * D'où une méthode à part entière plutôt qu'un {@code annuler(...)}, car le
     * vocabulaire de la passerelle doit dire ce qui se passe réellement.</p>
     *
     * <p>La clé d'idempotence portée par la demande est <b>dérivée du paiement</b>,
     * donc stable d'un appel à l'autre : c'est ce qui empêche un rejeu de rembourser
     * deux fois, là où une clé tirée au hasard offrirait la garantie inverse.</p>
     */
    RemboursementCree rembourser(DemandeRemboursement demande);
}
