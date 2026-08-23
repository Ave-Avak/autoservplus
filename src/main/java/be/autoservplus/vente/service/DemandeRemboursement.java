package be.autoservplus.vente.service;

import java.math.BigDecimal;

/**
 * Elements necessaires au remboursement d un paiement encaisse (F30). Des chaines
 * et montants plats, pas d entite : la passerelle ne connait pas le modele de
 * domaine, meme principe que {@link DemandePaiement}.
 *
 * @param referencePrestataire identifiant du paiement d origine chez le prestataire
 *                             (c est lui qu on rembourse, pas la commande)
 * @param montantTvac          montant a rendre, TVAC. Perimetre V1 : toujours la
 *                             totalite de l encaissement, l annulation partielle
 *                             etant reportee en V2. Le champ est neanmoins explicite
 *                             plutot qu implicite, pour que le remboursement partiel
 *                             n oblige pas a changer la signature.
 * @param devise               code ISO 4217 (EUR en V1)
 * @param cleIdempotence       cle stable derivee du paiement : une requete rejouee
 *                             vers le prestataire ne rembourse pas deux fois
 */
public record DemandeRemboursement(String referencePrestataire,
                                   BigDecimal montantTvac,
                                   String devise,
                                   String cleIdempotence) {
}
