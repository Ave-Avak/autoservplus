package be.autoservplus.communication.service;

/**
 * Elements du courriel confirmant la suppression d un compte (F23). Chaines plates,
 * comme {@link DetailsPaiementCourriel} : le module communication ne depend ni de
 * l identite ni du module rgpd.
 *
 * <p>L adresse et le prenom sont ceux <b>captures avant l anonymisation</b>. Les
 * relire apres coup ne donnerait rien : l anonymisation vient de les effacer, et la
 * ligne rechargee porte un jeton non routable.</p>
 *
 * @param adresseEmail adresse reelle du membre, capturee avant effacement
 * @param prenom       prenom reel, capture avant effacement, pour la salutation
 */
public record DetailsSuppressionCompteCourriel(String adresseEmail, String prenom) {
}
