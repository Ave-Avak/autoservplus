package be.autoservplus.rgpd.service;

import java.util.UUID;

/**
 * Evenement applicatif publie apres l anonymisation d un compte (F23).
 *
 * <p><b>Il porte l adresse, contrairement aux autres evenements du projet</b>, qui ne
 * transportent qu une reference et laissent le listener recharger. Ici recharger ne
 * donnerait rien : l anonymisation vient precisement d effacer l adresse, et la ligne
 * rechargee porterait le jeton {@code anonyme-…@supprime.invalid}, non routable. Le
 * service capture donc l adresse et le prenom <b>avant</b> l ecrasement, et les
 * confie a l evenement.</p>
 *
 * <p>Arbitrage assume avec le CdC, qui demande un courriel « avant la suppression
 * effective ». Le patron du projet interdit l envoi dans la transaction — un
 * fournisseur de courriel indisponible ne doit pas annuler un droit exerce. La
 * capture avant ecrasement puis l envoi apres commit respectent les deux : le membre
 * est prevenu, et l envoi reste decouple. L ecart porte sur l instant, pas sur le
 * fait.</p>
 *
 * <p>La reference du compte accompagne l adresse pour les journaux : elle identifie
 * la ligne sans reveler personne, et c est elle qui figure dans la trace applicative
 * de la suppression.</p>
 *
 * @param adresseEmail adresse reelle, capturee avant anonymisation
 * @param prenom       prenom reel, capture avant anonymisation, pour la salutation
 * @param reference    identifiant public du compte anonymise
 */
public record CompteSupprimeEvent(String adresseEmail, String prenom, UUID reference) {
}
