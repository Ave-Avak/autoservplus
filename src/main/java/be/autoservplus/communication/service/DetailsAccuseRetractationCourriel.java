package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Langue;

import java.time.Instant;

/**
 * Éléments de l'accusé de réception d'une rétractation en ligne (CDE, art. VI.61/2,
 * §4) : le contenu de la déclaration, la date et l'heure de sa soumission.
 *
 * @param adresseEmail   adresse du membre, celle de la déclaration
 * @param prenom         prénom du membre, pour la salutation
 * @param langue         langue du compte, celle du courriel
 * @param nomMembre      nom du consommateur tel que la déclaration l'a montré
 * @param numeroCommande identification du contrat
 * @param motif          motif donné par le membre, ou {@code null} : il est facultatif
 * @param soumiseLe      instant de la soumission, formaté dans le fuseau de l'atelier
 */
public record DetailsAccuseRetractationCourriel(String adresseEmail,
                                                String prenom,
                                                Langue langue,
                                                String nomMembre,
                                                String numeroCommande,
                                                String motif,
                                                Instant soumiseLe) {
}
