package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Langue;

/**
 * Éléments du courriel confirmant la suppression d'un compte (F23). Chaînes plates,
 * comme {@link DetailsConfirmationCommande} : le module communication ne dépend ni de
 * l'identité ni du module rgpd.
 *
 * <p>L'adresse et le prénom sont ceux <b>capturés avant l'anonymisation</b>. Les
 * relire après coup ne donnerait rien : l'anonymisation vient de les effacer, et la
 * ligne rechargée porte un jeton non routable.</p>
 *
 * @param adresseEmail adresse réelle du membre, capturée avant effacement
 * @param prenom       prénom réel, capturé avant effacement, pour la salutation
 * @param langue       langue du compte, celle du courriel
 */
public record DetailsSuppressionCompteCourriel(String adresseEmail, String prenom,
                                               Langue langue) {
}
