package be.autoservplus.messagerie.service.dto;

import be.autoservplus.messagerie.domain.Message;
import be.autoservplus.messagerie.domain.RoleExpediteur;

/**
 * Un message tel qu il s affiche dans un fil (BL-5).
 *
 * <p>{@code duGarage} plutot que le nom de l expediteur : cote membre, le garage
 * parle d une seule voix — savoir quel employe a repondu n apporte rien au client et
 * exposerait le personnel. Cote garage, le prenom du membre suffit a situer le fil.</p>
 */
public record MessageVue(
        String auteur,
        boolean duGarage,
        String corps,
        boolean lu,
        String date) {

    public static MessageVue de(Message message, String date, String libelleGarage) {
        boolean duGarage = message.getRole() == RoleExpediteur.ADMINISTRATEUR;
        return new MessageVue(
                duGarage ? libelleGarage : message.getExpediteur().getPrenom(),
                duGarage,
                message.getCorps(),
                message.isLu(),
                date);
    }
}
