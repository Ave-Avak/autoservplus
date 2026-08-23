package be.autoservplus.communication.service;

/**
 * Elements necessaires au courriel de cloture d une intervention (F17) : le membre
 * est invite a venir recuperer son vehicule.
 *
 * <p>Meme principe que {@link DetailsRdvCourriel} : des chaines deja pretes, pour que
 * le module {@code communication} ne depende pas du module {@code intervention}.
 * Le destinataire arrive lui aussi en chaines plates (adresse, prenom) plutot qu en
 * entite : l appelant est un listener post-commit qui recharge ses donnees dans sa
 * propre transaction, le courriel n a pas a toucher une entite potentiellement lazy.</p>
 *
 * @param adresseEmail       adresse du membre proprietaire
 * @param prenom             prenom du membre, pour la salutation
 * @param numeroIntervention numero lisible de l intervention (INT-...)
 * @param libelleVehicule    marque et modele, tels qu affiches dans le suivi
 * @param immatriculation    plaque du vehicule a recuperer
 * @param cheminDepotAvis    chemin relatif du formulaire de depot d avis (BL-4).
 *                           Une chaine deja prete, comme le reste : le module
 *                           communication ne construit pas d URL et ne connait pas
 *                           les routes du module intervention
 */
public record DetailsInterventionTerminee(String adresseEmail,
                                          String prenom,
                                          String numeroIntervention,
                                          String libelleVehicule,
                                          String immatriculation,
                                          String cheminDepotAvis) {
}
