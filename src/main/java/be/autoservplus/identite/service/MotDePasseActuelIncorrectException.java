package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Le mot de passe actuel saisi sur l'écran de changement ne correspond pas. Type à part
 * pour que le contrôleur compte ces échecs, et eux seuls : un nouveau mot de passe hors
 * politique n'apprend rien sur l'actuel.
 */
public class MotDePasseActuelIncorrectException extends RefusTraduit {

    public MotDePasseActuelIncorrectException() {
        super("mot-de-passe-changement.erreur.actuel", "Le mot de passe actuel est incorrect.");
    }
}
