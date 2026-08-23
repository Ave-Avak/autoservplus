package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * La seconde confirmation de la suppression de compte n a pas ete saisie (F23).
 *
 * <p>Le mot de passe prouve l identite ; il ne prouve pas l intention. Une session
 * ouverte sur un poste laisse sans surveillance suffit a un clic, et la suppression
 * est irreversible : les documents comptables sont conserves mais le compte ne
 * revient pas. Recopier un mot exact demande un geste delibere que ni un clic
 * accidentel ni un formulaire pre-rempli ne produisent.</p>
 *
 * <p>Pas de code RM : la garde vient de la fonctionnalite F23, pas d une regle
 * numerotee du CdC — meme raisonnement que {@link ReauthentificationEchoueeException}.</p>
 */
public class ConfirmationSuppressionInvalideException extends RegleMetierException {

    public ConfirmationSuppressionInvalideException() {
        super("Confirmation absente ou incorrecte : aucun compte n a ete supprime.");
    }
}
