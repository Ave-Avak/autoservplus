package be.autoservplus.communication.service;

/**
 * Dernière étape d'un courriel : la remise à un relais SMTP, ou à défaut le journal.
 *
 * <p>Une implémentation ne lève jamais : un échec de remise est journalisé et la suite
 * continue. L'action qui a demandé le courriel est déjà validée en base quand il part.</p>
 */
public interface TransportCourriel {

    void transmettre(Courriel courriel);
}
