package be.autoservplus.communication.service;

/**
 * Elements des deux courriels du changement d adresse (CdC 5.2.2). Chaines plates,
 * comme {@link DetailsSuppressionCompteCourriel} : le module communication ne depend
 * pas de l identite.
 *
 * <p><b>Les adresses sont des parametres et non des lectures de l entite.</b> Le
 * message de confirmation part vers une adresse qui n est pas encore celle du compte,
 * et l avis de bascule part vers une adresse que la ligne ne porte plus. Dans les deux
 * cas, relire {@code utilisateur.email} viserait le mauvais destinataire — c est le
 * meme piege que pour la suppression de compte.</p>
 *
 * @param destinataire   adresse a laquelle ce message precis doit partir
 * @param prenom         prenom du membre, pour la salutation
 * @param ancienneAdresse adresse en vigueur au moment de la demande
 * @param nouvelleAdresse adresse demandee
 */
public record DetailsChangementEmailCourriel(String destinataire, String prenom,
                                             String ancienneAdresse, String nouvelleAdresse) {
}
