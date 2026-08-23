package be.autoservplus.messagerie.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * Un fil de discussion tel qu il s affiche (BL-5).
 *
 * <p>{@code messages} est vide dans la vue de liste et rempli dans la vue de detail :
 * charger tous les messages de tous les fils pour n afficher que des titres ferait
 * autant de requetes que de fils.</p>
 *
 * @param numeroIntervention numero des travaux rattaches, ou {@code null} pour un fil
 *                           libre — le socle ne prevoit pas d autre accroche
 */
public record ConversationVue(
        UUID reference,
        String sujet,
        String prenomMembre,
        String numeroIntervention,
        boolean cloturee,
        long nombreNonLus,
        String derniereActivite,
        List<MessageVue> messages) {

    public boolean estRattachee() {
        return numeroIntervention != null;
    }

    public boolean aDesNonLus() {
        return nombreNonLus > 0;
    }
}
