package be.autoservplus.vente.service;

import java.math.BigDecimal;

/**
 * Elements necessaires a la creation d un paiement chez le prestataire. Des
 * chaines et montants plats, pas d entite : la passerelle ne connait pas le
 * modele de domaine (meme principe que les records {@code Details*Courriel}).
 *
 * @param numeroCommande numero lisible, affiche au membre sur la page de paiement
 * @param montantTvac    montant a encaisser, TVAC
 * @param devise         code ISO 4217 (EUR en V1)
 * @param cleIdempotence cle unique du paiement : une requete rejouee vers le
 *                       prestataire ne debite pas deux fois
 */
public record DemandePaiement(String numeroCommande,
                              BigDecimal montantTvac,
                              String devise,
                              String cleIdempotence) {
}
