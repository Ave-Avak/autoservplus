package be.autoservplus.vitrine.web.dto;

import java.util.List;

/**
 * Ouverture du garage pour un jour de la semaine, telle qu elle s affiche sur la
 * vitrine publique.
 *
 * @param cleJour  cle i18n du nom du jour ({@code vitrine.jour.lundi} ...). Le nom
 *                 n est pas resolu ici : la vue est construite par le service, qui
 *                 ne connait pas la langue d affichage — c est le gabarit, donc la
 *                 locale de la requete, qui tranche.
 * @param plages   intervalles horaires du jour, deja formates {@code HH:mm}, dans
 *                 l ordre. Une journee peut en compter plusieurs (matin,
 *                 apres-midi) ; la liste est vide si le garage est ferme.
 * @param ferme    vrai si aucune plage active ne couvre ce jour. Redondant avec
 *                 {@code plages.isEmpty()}, mais nomme l intention pour le gabarit,
 *                 qui affiche « Fermé » plutot qu une ligne vide.
 */
public record JourOuvertureVue(String cleJour, List<String> plages, boolean ferme) {
}
