package be.autoservplus.communication.service;

/**
 * Elements du courriel de confirmation de paiement (F14). Chaines plates, deja
 * formatees : meme principe que {@link DetailsInterventionTerminee}, le module
 * communication ne depend pas du module vente.
 *
 * @param adresseEmail   adresse du membre
 * @param prenom         prenom du membre, pour la salutation
 * @param numeroCommande numero lisible de la commande payee (CMD-...)
 * @param montantTvac    montant encaisse, formate en euros TVAC
 */
public record DetailsPaiementCourriel(String adresseEmail,
                                      String prenom,
                                      String numeroCommande,
                                      String montantTvac) {
}
