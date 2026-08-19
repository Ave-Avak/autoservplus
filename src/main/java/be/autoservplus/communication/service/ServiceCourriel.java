package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Utilisateur;

/**
 * Envoi des courriels transactionnels.
 *
 * <p>Le code metier depend de cette interface, jamais d un fournisseur particulier.
 * L implementation active est choisie par le profil Spring : journalisation en console
 * en developpement, envoi reel en production. Ce decouplage permet de tester les
 * services sans emettre le moindre message.</p>
 */
public interface ServiceCourriel {

    void envoyerVerificationAdresse(Utilisateur destinataire, String lienVerification);

    void envoyerReinitialisationMotDePasse(Utilisateur destinataire, String lienReinitialisation);
}