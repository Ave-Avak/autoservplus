package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Langue;

/**
 * Elements du courriel de confirmation de paiement (F14). Chaines plates, deja
 * formatees : meme principe que {@link DetailsInterventionTerminee}, le module
 * communication ne depend pas du module vente.
 *
 * @param adresseEmail   adresse du membre
 * @param prenom         prenom du membre, pour la salutation
 * @param langue         langue du compte, celle du courriel
 * @param numeroCommande numero lisible de la commande payee (CMD-...)
 * @param montantTvac    montant encaisse, formate en euros TVAC
 */
public record DetailsPaiementCourriel(String adresseEmail,
                                      String prenom,
                                      Langue langue,
                                      String numeroCommande,
                                      String montantTvac) {
}
