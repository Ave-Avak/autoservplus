package be.autoservplus.communication.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Consumer;

/**
 * Fait partir un courriel après la validation de la transaction qui l'a demandé, ou
 * tout de suite hors transaction.
 *
 * <p>Envoyé avant, un courriel annoncerait ce qu'un rollback peut encore défaire, par
 * exemple un lien vers un jeton jamais enregistré, et l'appel réseau garderait une
 * connexion à la base ouverte. L'envoi ordinaire reste synchrone : la requête attend la
 * remise, bornée par les délais SMTP configurés, mais ne tient plus aucune transaction.
 * Les courriels dont la durée trahirait l'existence d'un compte partent, eux, dans un
 * autre fil ({@link ExecuteurCourriels}), toujours après la validation.</p>
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
    private final Consumer<Courriel> horsRequete;

    @Autowired
    public EnvoiApresCommit(TransportCourriel transport, ExecuteurCourriels executeur) {
        this(transport, executeur::soumettre);
    }

    /** Envoi hors de la requête remplacé par l'envoi direct, pour les tests unitaires. */
    EnvoiApresCommit(TransportCourriel transport) {
        this(transport, transport::transmettre);
    }

    EnvoiApresCommit(TransportCourriel transport, Consumer<Courriel> horsRequete) {
        this.transport = transport;
        this.horsRequete = horsRequete;
    }

    /** Envoi dans le fil courant, après la validation. */
    public void planifier(Courriel courriel) {
        apresValidation(() -> transport.transmettre(courriel));
    }

    /** Envoi confié à un autre fil, après la validation. */
    public void planifierHorsRequete(Courriel courriel) {
        apresValidation(() -> horsRequete.accept(courriel));
    }

    private static void apresValidation(Runnable envoi) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envoi.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int statut) {
                if (statut == STATUS_COMMITTED) {
                    envoi.run();
                }
            }
        });
    }
}
