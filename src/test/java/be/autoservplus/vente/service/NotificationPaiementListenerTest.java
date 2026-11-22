package be.autoservplus.vente.service;

import be.autoservplus.communication.service.DetailsCommandeGarage;
import be.autoservplus.communication.service.DetailsConfirmationCommande;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPaiementListener")
class NotificationPaiementListenerTest {

    @Mock private CommandeRepository commandes;
    @Mock private ServiceCourriel courriel;
    @Mock private ConfirmationCommande confirmation;
    @Mock private be.autoservplus.vente.repository.PaiementRepository paiements;

    private NotificationPaiementListener listener;
    private Commande commande;

    @BeforeEach
    void setUp() {
        listener = new NotificationPaiementListener(commandes, courriel, confirmation, paiements);
        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                Instant.parse("2026-09-14T09:00:00Z"));
    }

    @Test
    @DisplayName("recharge la commande et envoie au membre la confirmation composee pour elle")
    void envoieLaConfirmation() {
        DetailsConfirmationCommande composee = mock(DetailsConfirmationCommande.class);
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));
        when(confirmation.composer(commande)).thenReturn(composee);

        listener.surCommandePayee(new CommandePayeeEvent(commande.getReference()));

        verify(courriel).envoyerConfirmationCommande(composee);
    }

    @Test
    @DisplayName("confirmation impossible a composer : avalee, l'avis au garage part quand meme")
    void compositionEnEchecAbsorbee() {
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));
        when(confirmation.composer(commande)).thenThrow(new IllegalStateException("PDF"));

        assertThatCode(() -> listener.surCommandePayee(
                new CommandePayeeEvent(commande.getReference())))
                .doesNotThrowAnyException();
        verify(courriel).envoyerAvisCommandeAuGarage(any());
    }

    @Test
    @DisplayName("paiement reçu après annulation : un avis au membre, un avis au garage")
    void avisDuPaiementApresAnnulation() {
        be.autoservplus.vente.domain.Paiement paiement = new be.autoservplus.vente.domain.Paiement(
                commande, commande.getMontantTvac(), Instant.parse("2026-09-14T09:00:00Z"));
        when(paiements.findByReference(paiement.getReference())).thenReturn(Optional.of(paiement));
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(courriel).envoyerAvisPaiementApresAnnulation(any(), any(), any());

        assertThatCode(() -> listener.surPaiementApresAnnulation(new PaiementApresAnnulationEvent(
                paiement.getReference(), commande.getReference()))).doesNotThrowAnyException();

        verify(courriel).envoyerAvisPaiementApresAnnulation(commande.getMembre(),
                "CMD-2026-0001", be.autoservplus.reservation.service.support.FormatageRdv.euros(commande.getMontantTvac()));
        // L'échec du premier envoi n'empêche pas le second.
        verify(courriel).envoyerAvisPaiementARembourserAuGarage("CMD-2026-0001", be.autoservplus.reservation.service.support.FormatageRdv.euros(commande.getMontantTvac()),
                commande.getMembre().nomComplet());
    }

    @Test
    @DisplayName("remboursement après annulation : le membre est prévenu, un paiement introuvable est avalé")
    void avisDuRemboursement() {
        be.autoservplus.vente.domain.Paiement paiement = new be.autoservplus.vente.domain.Paiement(
                commande, commande.getMontantTvac(), Instant.parse("2026-09-14T09:00:00Z"));
        when(paiements.findByReference(paiement.getReference())).thenReturn(Optional.of(paiement));

        listener.surRemboursementApresAnnulation(
                new RemboursementApresAnnulationEvent(paiement.getReference()));
        verify(courriel).envoyerAvisRemboursementApresAnnulation(commande.getMembre(),
                "CMD-2026-0001", be.autoservplus.reservation.service.support.FormatageRdv.euros(commande.getMontantTvac()));

        UUID inconnu = UUID.randomUUID();
        when(paiements.findByReference(inconnu)).thenReturn(Optional.empty());
        assertThatCode(() -> {
            listener.surRemboursementApresAnnulation(new RemboursementApresAnnulationEvent(inconnu));
            listener.surPaiementApresAnnulation(new PaiementApresAnnulationEvent(inconnu, inconnu));
        }).doesNotThrowAnyException();
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
                .when(courriel).envoyerConfirmationCommande(any());

        assertThatCode(() -> listener.surCommandePayee(
                new CommandePayeeEvent(commande.getReference())))
                .doesNotThrowAnyException();
        // Le reçu du membre en panne n'empêche pas l'avis au garage.
        verify(courriel).envoyerAvisCommandeAuGarage(any());
    }

    @Test
    @DisplayName("avis au garage : numéro, montant, membre, pièces à préparer")
    void avisAuGaragePieces() {
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of());

        listener.surCommandePayee(new CommandePayeeEvent(commande.getReference()));

        ArgumentCaptor<DetailsCommandeGarage> captor = ArgumentCaptor.forClass(DetailsCommandeGarage.class);
        verify(courriel).envoyerAvisCommandeAuGarage(captor.capture());
        DetailsCommandeGarage avis = captor.getValue();
        assertThat(avis.numeroCommande()).isEqualTo("CMD-2026-0001");
        assertThat(avis.montantTvac()).contains("80,21");
        assertThat(avis.membre()).isEqualTo("Marie Dupont");
        assertThat(avis.prestations()).isFalse();
        assertThat(avis.ruptureAHonorer()).isFalse();
    }

    @Test
    @DisplayName("avis au garage : prestations à planifier et rupture à honorer signalées")
    void avisAuGaragePrestationsEtRupture() {
        commande.signalerRupture();
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of(mock(LignePanier.class)));

        listener.surCommandePayee(new CommandePayeeEvent(commande.getReference()));

        ArgumentCaptor<DetailsCommandeGarage> captor = ArgumentCaptor.forClass(DetailsCommandeGarage.class);
        verify(courriel).envoyerAvisCommandeAuGarage(captor.capture());
        assertThat(captor.getValue().prestations()).isTrue();
        assertThat(captor.getValue().ruptureAHonorer()).isTrue();
    }

    @Test
    @DisplayName("avis au garage en panne : avalé, le reçu du membre est parti")
    void exceptionDeLAvisAbsorbee() {
        when(commandes.findByReference(commande.getReference()))
                .thenReturn(Optional.of(commande));
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(courriel).envoyerAvisCommandeAuGarage(any());

        assertThatCode(() -> listener.surCommandePayee(
                new CommandePayeeEvent(commande.getReference())))
                .doesNotThrowAnyException();
        verify(courriel).envoyerConfirmationCommande(any());
    }
}
