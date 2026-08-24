package be.autoservplus.vente.service;

import java.math.BigDecimal;

/**
 * Elements necessaires a la creation d un paiement chez le prestataire. Des
 * chaines et montants plats, pas d entite : la passerelle ne connait pas le
 * modele de domaine (meme principe que les records {@code Details*Courriel}).
 *
 * <p><b>Les deux URL sont fournies, pas calculees par la passerelle.</b> Elles
 * appartiennent a l espace d adressage d AutoServ+, pas au vocabulaire du
 * prestataire : les composer dans la passerelle y ferait entrer la connaissance
 * de nos routes, et un second prestataire les recalculerait a l identique. Le
 * service les derive une fois de {@code autoservplus.url-publique}.</p>
 *
 * @param numeroCommande   numero lisible, affiche au membre sur la page de paiement
 * @param montantTvac      montant a encaisser, TVAC
 * @param devise           code ISO 4217 (EUR en V1)
 * @param cleIdempotence   cle unique du paiement : une requete rejouee vers le
 *                         prestataire ne debite pas deux fois
 * @param urlRetour        adresse ou le prestataire renvoie le membre une fois la
 *                         page de paiement quittee, quelle qu en soit l issue. Ce
 *                         retour n est PAS une preuve de paiement : il survient
 *                         aussi apres un abandon.
 * @param urlNotification  adresse de notification serveur a serveur. La passerelle
 *                         reste libre de ne pas la transmettre lorsque le
 *                         prestataire ne pourrait pas la joindre — regle propre au
 *                         prestataire, donc tranchee chez lui.
 */
public record DemandePaiement(String numeroCommande,
                              BigDecimal montantTvac,
                              String devise,
                              String cleIdempotence,
                              String urlRetour,
                              String urlNotification) {
}
