package be.autoservplus.communication.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fait partir un courriel après la validation de la transaction qui l'a demandé, ou
 * tout de suite hors transaction.
 *
 * <p>Envoyé avant, un courriel annoncerait ce qu'un rollback peut encore défaire, par
 * exemple un lien vers un jeton jamais enregistré, et l'appel réseau garderait une
 * connexion à la base ouverte. L'envoi reste synchrone : la requête attend la remise,
 * bornée par les délais SMTP configurés, mais ne tient plus aucune transaction.</p>
 *
 * <p>{@code afterCompletion} avec le statut validé, et non {@code afterCommit} : Spring
 * relit la liste des synchronisations à cette étape. Un courriel demandé depuis un
 * écouteur {@code AFTER_COMMIT} sans transaction propre, comme la confirmation de
 * suppression de compte, s'enregistre pendant la phase {@code afterCommit} et serait
 * perdu s'il attendait cette même phase.</p>
 */
@Component
public class EnvoiApresCommit {

    private final TransportCourriel transport;

    public EnvoiApresCommit(TransportCourriel transport) {
        this.transport = transport;
    }

    public void planifier(Courriel courriel) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            transport.transmettre(courriel);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int statut) {
                if (statut == STATUS_COMMITTED) {
                    transport.transmettre(courriel);
                }
            }
        });
    }
}
