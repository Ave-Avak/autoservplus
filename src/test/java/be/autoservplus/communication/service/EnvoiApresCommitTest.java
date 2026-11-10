package be.autoservplus.communication.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le courriel attend la fin de la transaction et ne part que si elle est validée. Le
 * cas d'un enregistrement pendant la phase {@code afterCommit} est tenu par le test
 * d'intégration de la suppression de compte, sur un vrai gestionnaire de transactions.
 */
@DisplayName("Envoi après la validation de la transaction")
class EnvoiApresCommitTest {

    private final List<Courriel> partis = new ArrayList<>();
    private final EnvoiApresCommit envoi = new EnvoiApresCommit(partis::add);
    private final Courriel courriel = new Courriel("essai", "a@exemple.be", "A", "S", "C", null);

    @AfterEach
    void nettoyer() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void terminer(int statut) {
        List<TransactionSynchronization> synchronisations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronisations.forEach(s -> s.afterCompletion(statut));
    }

    @Test
    @DisplayName("hors transaction, le courriel part aussitôt")
    void horsTransaction() {
        envoi.planifier(courriel);

        assertThat(partis).containsExactly(courriel);
    }

    @Test
    @DisplayName("dans une transaction, il attend sa validation")
    void apresValidation() {
        TransactionSynchronizationManager.initSynchronization();

        envoi.planifier(courriel);
        assertThat(partis).isEmpty();

        terminer(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(partis).containsExactly(courriel);
    }

    @Test
    @DisplayName("une transaction annulée n'envoie rien")
    void annulation() {
        TransactionSynchronizationManager.initSynchronization();

        envoi.planifier(courriel);
        terminer(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(partis).isEmpty();
    }
}
