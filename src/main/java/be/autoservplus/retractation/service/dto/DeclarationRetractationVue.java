package be.autoservplus.retractation.service.dto;

import java.util.UUID;

/**
 * Ce que la déclaration de rétractation en ligne montre au membre avant qu'il la
 * confirme (CDE, art. VI.61/2, §2) : son nom, le contrat dont il se rétracte, et
 * l'adresse électronique où l'accusé de réception lui sera envoyé.
 *
 * @param reference           référence de la commande, pour les adresses de l'écran
 * @param numeroCommande      identification du contrat
 * @param totalTvac           montant de la commande, formaté
 * @param nomMembre           nom du consommateur
 * @param adresseConfirmation adresse électronique de l'accusé de réception
 */
public record DeclarationRetractationVue(UUID reference,
                                         String numeroCommande,
                                         String totalTvac,
                                         String nomMembre,
                                         String adresseConfirmation) {
}
