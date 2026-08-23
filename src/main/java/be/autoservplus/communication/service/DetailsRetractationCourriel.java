package be.autoservplus.communication.service;

/**
 * Elements du courriel annoncant la decision du garage sur une demande de
 * retractation (F30). Chaines plates, deja formatees : meme principe que
 * {@link DetailsPaiementCourriel}, le module communication ne depend ni de la vente
 * ni de la facturation.
 *
 * <p>Un seul record pour l acceptation et le refus, {@code acceptee} tranchant : ce
 * sont deux issues du meme dossier, avec le meme destinataire et les memes
 * references. Deux records auraient duplique quatre champs sur cinq.</p>
 *
 * @param adresseEmail   adresse du membre
 * @param prenom         prenom du membre, pour la salutation
 * @param numeroCommande numero lisible de la commande concernee (CMD-...)
 * @param montantTvac    montant rembourse, formate en euros TVAC ; {@code null} au refus
 * @param acceptee       {@code true} si la retractation est acceptee
 * @param numeroAvoir    numero de la note de credit (AV-...), {@code null} au refus
 * @param motifRefus     constat oppose au membre, {@code null} a l acceptation
 */
public record DetailsRetractationCourriel(String adresseEmail,
                                          String prenom,
                                          String numeroCommande,
                                          String montantTvac,
                                          boolean acceptee,
                                          String numeroAvoir,
                                          String motifRefus) {
}
