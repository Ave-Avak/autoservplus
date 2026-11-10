package be.autoservplus.communication.service;

/** Chemin par lequel le mot de passe a changé, pour l'avis envoyé au titulaire. */
public enum OrigineMotDePasse {

    /** Par le lien de réinitialisation reçu par courriel. */
    REINITIALISATION,

    /** Par le changement imposé au premier accès. */
    CHANGEMENT_IMPOSE
}
