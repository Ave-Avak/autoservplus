package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.service.CommandePayeeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Raccordement de l emission a l evenement de paiement. Le contrat transactionnel
 * lui-meme (AFTER_COMMIT) se prouve contre une vraie base : {@code EmissionFactureIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FacturationPaiementListener")
class FacturationPaiementListenerTest {

    @Mock private FactureService factures;
    @InjectMocks private FacturationPaiementListener listener;

    private final UUID reference = UUID.randomUUID();

    private Facture facture() {
        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                Instant.parse("2026-08-22T14:00:00Z"));
        return Facture.pourCommande("2026-0001", (short) 2026, 1, commande,
                new BigDecimal("21.00"), Instant.parse("2026-08-22T14:30:00Z"));
    }

    @Test
    @DisplayName("emet la facture de la commande payee")
    void emetLaFacture() {
        when(factures.emettrePourCommande(reference)).thenReturn(facture());

        listener.surCommandePayee(new CommandePayeeEvent(reference));

        verify(factures).emettrePourCommande(reference);
    }

    @Test
    @DisplayName("course perdue sur l'unicite : la facture existe deja, rien a reprendre")
    void courseSurLUnicite() {
        // L index partiel uq_facture_commande a refuse la seconde emission simultanee.
        when(factures.emettrePourCommande(reference))
                .thenThrow(new DataIntegrityViolationException("uq_facture_commande"));

        assertThatCode(() -> listener.surCommandePayee(new CommandePayeeEvent(reference)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un echec d'emission ne remonte pas : l'encaissement reste acquis")
    void echecAvale() {
        when(factures.emettrePourCommande(reference))
                .thenThrow(new IllegalStateException("base indisponible"));

        // Le paiement est committe ; laisser l exception remonter ne le defairait
        // pas et polluerait le traitement du webhook. L erreur part en journal.
        assertThatCode(() -> listener.surCommandePayee(new CommandePayeeEvent(reference)))
                .doesNotThrowAnyException();
    }
}
