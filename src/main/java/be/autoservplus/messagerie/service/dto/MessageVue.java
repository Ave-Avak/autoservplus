package be.autoservplus.messagerie.service.dto;

import be.autoservplus.messagerie.domain.Message;
import be.autoservplus.messagerie.domain.RoleExpediteur;

import java.time.LocalDateTime;

/**
 * Un message tel qu il s affiche dans un fil (BL-5).
 *
 * <p>{@code duGarage} plutot que le nom de l expediteur : cote membre, le garage
 * parle d une seule voix, savoir quel employe a repondu n apporte rien au client et
 * exposerait le personnel. Cote garage, le prenom du membre suffit a situer le fil.</p>
 */
public record MessageVue(
        /** Identifiant du message, curseur du rafraîchissement : le fil ne rend que les suivants. */
        Long id,
        String auteur,
        boolean duGarage,
        String corps,
        boolean lu,
        LocalDateTime date) {

    public static MessageVue de(Message message, LocalDateTime date, String libelleGarage) {
        boolean duGarage = message.getRole() == RoleExpediteur.ADMINISTRATEUR;
        return new MessageVue(
                message.getId(),
                duGarage ? libelleGarage : message.getExpediteur().getPrenom(),
                duGarage,
                message.getCorps(),
                message.isLu(),
                date);
    }
}
