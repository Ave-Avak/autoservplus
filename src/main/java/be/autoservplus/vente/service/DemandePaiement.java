package be.autoservplus.vente.service;

import java.math.BigDecimal;

/**
 * Éléments nécessaires à la création d'un paiement chez le prestataire. Des
 * chaînes et montants plats, pas d'entité : la passerelle ne connaît pas le
 * modèle de domaine (même principe que les records {@code Details*Courriel}).
 *
 * <p><b>Les deux URL sont fournies, pas calculées par la passerelle.</b> Elles
 * appartiennent à l'espace d'adressage d'AutoServ+, pas au vocabulaire du
 * prestataire : les composer dans la passerelle y ferait entrer la connaissance
 * de nos routes, et un second prestataire les recalculerait à l'identique. Le
 * service les dérive une fois de {@code autoservplus.url-publique}.</p>
 *
 * @param numeroCommande   numéro lisible, affiché au membre sur la page de paiement
 * @param montantTvac      montant à encaisser, TVAC
 * @param devise           code ISO 4217 (EUR en V1)
 * @param cleIdempotence   clé unique du paiement : une requête rejouée vers le
 *                         prestataire ne débite pas deux fois
 * @param urlRetour        adresse où le prestataire renvoie le membre une fois la
 *                         page de paiement quittée, quelle qu'en soit l'issue. Ce
 *                         retour n'est PAS une preuve de paiement : il survient
 *                         aussi après un abandon.
 * @param urlNotification  adresse de notification serveur à serveur. La passerelle
 *                         reste libre de ne pas la transmettre lorsque le
 *                         prestataire ne pourrait pas la joindre : règle propre au
 *                         prestataire, donc tranchée chez lui.
 */
public record DemandePaiement(String numeroCommande,
                              BigDecimal montantTvac,
                              String devise,
                              String cleIdempotence,
                              String urlRetour,
                              String urlNotification) {
}
