package be.autoservplus.vente.service;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpirationCommandesJob (RM-21)")
class ExpirationCommandesJobTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    @Mock private CommandeRepository commandes;
    @Mock private PaiementRepository paiements;

    private ExpirationCommandesJob job;
    private Commande commande;
    private Paiement paiement;

    @BeforeEach
    void setUp() {
        job = new ExpirationCommandesJob(commandes, paiements,
                Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));

        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        // Creee il y a 31 minutes : au-dela du delai RM-21 de 30 minutes.
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                MAINTENANT.minus(Duration.ofMinutes(31)));
        ReflectionTestUtils.setField(commande, "id", 10L);
        paiement = new Paiement(commande, commande.getMontantTvac(),
                MAINTENANT.minus(Duration.ofMinutes(31)));
    }

    @Test
    @DisplayName("commande de plus de 30 min : ANNULEE motif TIMEOUT_PAIEMENT, paiement EXPIRE")
    void annuleLesCommandesExpirees() {
        when(commandes.parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), any()))
                .thenReturn(List.of(commande));
        when(commandes.verrouillerParId(10L)).thenReturn(Optional.of(commande));
        when(paiements.findByCommandeAndStatutIn(eq(commande), anyCollection()))
                .thenReturn(List.of(paiement));
        when(paiements.saveAndFlush(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commandes.saveAndFlush(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));

        job.annulerLesCommandesExpirees();

        assertThat(commande.getStatut()).isEqualTo(StatutCommande.ANNULEE);
        assertThat(commande.getMotifAnnulation()).isEqualTo(MotifAnnulationCommande.TIMEOUT_PAIEMENT);
        assertThat(commande.getDateAnnulation()).isEqualTo(MAINTENANT);
        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.EXPIRE);

        // La limite passee a la requete est bien maintenant - DELAI_PAIEMENT (horloge figee).
        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(commandes).parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), limite.capture());
        assertThat(limite.getValue())
                .isEqualTo(MAINTENANT.minus(ExpirationCommandesJob.DELAI_PAIEMENT));
    }

    /**
     * Course job / webhook : la commande etait EN_ATTENTE au scan, mais un webhook
     * « paid » a commite entre le scan et le verrou. La relecture sous verrou voit
     * PAYEE et le job passe son chemin — une PAYEE ne redevient jamais ANNULEE.
     */
    @Test
    @DisplayName("payee entre le scan et le verrou : le job passe, aucun etat incoherent")
    void payeeEntreScanEtVerrou() {
        when(commandes.parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), any()))
                .thenReturn(List.of(commande));
        commande.confirmerPaiement(MAINTENANT); // le webhook a gagne la course
        when(commandes.verrouillerParId(10L)).thenReturn(Optional.of(commande));

        job.annulerLesCommandesExpirees();

        assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(commande.getMotifAnnulation()).isNull();
        verify(commandes, never()).saveAndFlush(any());
        verify(paiements, never()).findByCommandeAndStatutIn(any(), anyCollection());
    }

    @Test
    @DisplayName("aucune candidate : le job ne touche a rien")
    void rienAExpirer() {
        when(commandes.parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), any()))
                .thenReturn(List.of());

        job.annulerLesCommandesExpirees();

        verify(commandes, never()).verrouillerParId(any());
        verify(commandes, never()).saveAndFlush(any());
    }
}
