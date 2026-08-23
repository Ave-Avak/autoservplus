package be.autoservplus.rgpd.service;

import be.autoservplus.communication.service.DetailsSuppressionCompteCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Notification de suppression de compte (F23) : elle part a l adresse <b>capturee</b>,
 * et son echec ne remonte pas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSuppressionListener")
class NotificationSuppressionListenerTest {

    @Mock private ServiceCourriel courriel;

    private final UUID reference = UUID.randomUUID();

    @Test
    @DisplayName("envoie a l'adresse portee par l'evenement, sans rien recharger")
    void envoiALAdresseCapturee() {
        new NotificationSuppressionListener(courriel).surCompteSupprime(
                new CompteSupprimeEvent("marie@exemple.be", "Marie", reference));

        ArgumentCaptor<DetailsSuppressionCompteCourriel> capture =
                ArgumentCaptor.forClass(DetailsSuppressionCompteCourriel.class);
        verify(courriel).envoyerConfirmationSuppressionCompte(capture.capture());
        // Recharger le compte ne rendrait que le jeton non routable : l evenement
        // porte l adresse precisement parce qu elle n existe plus en base.
        assertThat(capture.getValue().adresseEmail()).isEqualTo("marie@exemple.be");
        assertThat(capture.getValue().prenom()).isEqualTo("Marie");
    }

    @Test
    @DisplayName("un fournisseur en panne ne fait pas echouer la suppression")
    void echecDEnvoiAvale() {
        doThrow(new IllegalStateException("fournisseur indisponible"))
                .when(courriel).envoyerConfirmationSuppressionCompte(any());

        // AFTER_COMMIT : l anonymisation est deja committee, elle ne se rejoue pas.
        // Laisser remonter l exception ne changerait rien en base et polluerait les
        // journaux d une erreur qui n a aucune consequence pour la personne.
        assertThatCode(() -> new NotificationSuppressionListener(courriel).surCompteSupprime(
                new CompteSupprimeEvent("marie@exemple.be", "Marie", reference)))
                .doesNotThrowAnyException();
    }
}
