package be.autoservplus.vente.service;

import be.autoservplus.communication.service.DetailsPaiementCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPaiementListener")
class NotificationPaiementListenerTest {

    @Mock private CommandeRepository commandes;
    @Mock private ServiceCourriel courriel;

    private NotificationPaiementListener listener;
    private Commande commande;

    @BeforeEach
    void setUp() {
        listener = new NotificationPaiementListener(commandes, courriel);
        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                Instant.parse("2026-09-14T09:00:00Z"));
    }

    @Test
    @DisplayName("recharge la commande et envoie le recu au membre")
    void envoieLeRecu() {
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));

        listener.surCommandePayee(new CommandePayeeEvent(commande.getReference()));

        ArgumentCaptor<DetailsPaiementCourriel> captor =
                ArgumentCaptor.forClass(DetailsPaiementCourriel.class);
        verify(courriel).envoyerConfirmationPaiement(captor.capture());
        DetailsPaiementCourriel details = captor.getValue();
        assertThat(details.adresseEmail()).isEqualTo("marie@exemple.be");
        assertThat(details.prenom()).isEqualTo("Marie");
        assertThat(details.numeroCommande()).isEqualTo("CMD-2026-0001");
        assertThat(details.montantTvac()).contains("80,21");
    }

    @Test
    @DisplayName("commande introuvable apres commit : journalise, aucune exception")
    void commandeIntrouvableAbsorbee() {
        UUID reference = UUID.randomUUID();
        when(commandes.findByReference(reference)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.surCommandePayee(new CommandePayeeEvent(reference)))
                .doesNotThrowAnyException();
        verifyNoInteractions(courriel);
    }

    @Test
    @DisplayName("fournisseur mail en panne : avale, l'encaissement committe ne retombe pas en erreur")
    void exceptionDuCourrielAbsorbee() {
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(courriel).envoyerConfirmationPaiement(any());

        assertThatCode(() -> listener.surCommandePayee(
                new CommandePayeeEvent(commande.getReference())))
                .doesNotThrowAnyException();
    }
}
