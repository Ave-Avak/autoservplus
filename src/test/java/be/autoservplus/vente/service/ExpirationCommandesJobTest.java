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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les décisions du job, une lecture du prestataire à la fois. Le prestataire et le
 * service de paiement sont simulés : l'état lu est appliqué à la tentative comme le
 * service le ferait, pour que la revérification sous verrou voie ce qu'elle verrait
 * en base. {@code ExpirationCommandesIT} rejoue les mêmes cas sur la base réelle.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExpirationCommandesJob (RM-21)")
class ExpirationCommandesJobTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");
    private static final String REFERENCE = "tr_fictif_0001";

    @Mock private CommandeRepository commandes;
    @Mock private PaiementRepository paiements;
    @Mock private PrestatairePaiement prestataire;
    @Mock private PaiementService paiementService;

    private ExpirationCommandesJob job;
    private Commande commande;
    private final List<Paiement> tentatives = new ArrayList<>();

    @BeforeEach
    void setUp() {
        job = new ExpirationCommandesJob(commandes, paiements, prestataire, paiementService,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));

        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        // Créée il y a 31 minutes : au-delà du délai RM-21 de 30 minutes.
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                MAINTENANT.minus(Duration.ofMinutes(31)));
        ReflectionTestUtils.setField(commande, "id", 10L);

        when(commandes.parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), any()))
                .thenReturn(List.of(commande));
        when(commandes.findById(10L)).thenReturn(Optional.of(commande));
        when(commandes.verrouillerParId(10L)).thenReturn(Optional.of(commande));
        when(paiements.findByCommandeAndStatutIn(eq(commande), anyCollection()))
                .thenAnswer(appel -> tentatives.stream()
                        .filter(paiement -> !paiement.estTermine()).toList());
        // Ce que PaiementService.constater écrit sur la tentative, sans le reste.
        when(paiementService.constater(anyString(), any())).thenAnswer(appel -> {
            EtatPaiement etat = appel.getArgument(1);
            Paiement tentative = tentatives.get(0);
            switch (etat.statut()) {
                case REUSSI -> {
                    tentative.confirmer(MAINTENANT);
                    commande.confirmerPaiement(MAINTENANT);
                }
                case ECHOUE -> tentative.echouer(MAINTENANT);
                case EXPIRE -> tentative.expirer(MAINTENANT);
                case EN_COURS -> tentative.mettreEnCours();
                default -> {
                }
            }
            return null;
        });
    }

    /** Tentative partie chez le prestataire, créée il y a {@code age}. */
    private Paiement tentative(Duration age) {
        Paiement paiement = new Paiement(commande, commande.getMontantTvac(), MAINTENANT.minus(age));
        paiement.enregistrerReferencePrestataire(REFERENCE);
        tentatives.add(paiement);
        return paiement;
    }

    private void prestataireLit(StatutPaiement statut, boolean annulable) {
        when(prestataire.lireEtat(REFERENCE))
                .thenReturn(new EtatPaiement(statut, null, null, annulable));
    }

    private void assertAnnuleePourDelaiDepasse() {
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.ANNULEE);
        assertThat(commande.getMotifAnnulation()).isEqualTo(MotifAnnulationCommande.TIMEOUT_PAIEMENT);
        assertThat(commande.getDateAnnulation()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("aucune tentative : la commande est annulée à l'échéance, sans appel au prestataire")
    void sansTentative() {
        job.annulerLesCommandesExpirees();

        assertAnnuleePourDelaiDepasse();
        verify(prestataire, never()).lireEtat(anyString());

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(commandes).parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), limite.capture());
        assertThat(limite.getValue())
                .isEqualTo(MAINTENANT.minus(ExpirationCommandesJob.DELAI_PAIEMENT));
    }

    @Test
    @DisplayName("tentative jamais partie chez le prestataire : expirée avec la commande")
    void tentativeResteeSurLeSite() {
        Paiement locale = new Paiement(commande, commande.getMontantTvac(), MAINTENANT);
        tentatives.add(locale);

        job.annulerLesCommandesExpirees();

        assertAnnuleePourDelaiDepasse();
        assertThat(locale.getStatut()).isEqualTo(StatutPaiement.EXPIRE);
        verify(prestataire, never()).lireEtat(anyString());
    }

    @Test
    @DisplayName("payé chez le prestataire : traité comme la notification, rien n'est annulé")
    void paye() {
        tentative(Duration.ofMinutes(20));
        prestataireLit(StatutPaiement.REUSSI, false);

        job.annulerLesCommandesExpirees();

        assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
        verify(prestataire, never()).annuler(anyString());
        verify(commandes, never()).verrouillerParId(any());
    }

    @Test
    @DisplayName("ouvert et annulable : annulé chez le prestataire, puis la commande")
    void ouvertAnnulable() {
        Paiement paiement = tentative(Duration.ofMinutes(20));
        prestataireLit(StatutPaiement.INITIE, true);
        when(prestataire.annuler(REFERENCE)).thenReturn(EtatPaiement.de(StatutPaiement.ECHOUE));

        job.annulerLesCommandesExpirees();

        verify(prestataire).annuler(REFERENCE);
        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.ECHOUE);
        assertAnnuleePourDelaiDepasse();
    }

    @Test
    @DisplayName("ouvert non annulable : la commande attend le statut définitif")
    void ouvertNonAnnulable() {
        Paiement paiement = tentative(Duration.ofMinutes(20));
        prestataireLit(StatutPaiement.INITIE, false);

        job.annulerLesCommandesExpirees();

        verify(prestataire, never()).annuler(anyString());
        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.INITIE);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
    }

    @Test
    @DisplayName("en cours ou autorisé : jamais annulé chez le prestataire, la commande attend")
    void enCours() {
        tentative(Duration.ofMinutes(20));
        // Annulable ou non, un paiement que la banque traite ne s'annule pas.
        prestataireLit(StatutPaiement.EN_COURS, true);

        job.annulerLesCommandesExpirees();

        verify(prestataire, never()).annuler(anyString());
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
    }

    @Test
    @DisplayName("échoué ou expiré chez le prestataire : la commande est annulée")
    void statutFinal() {
        Paiement paiement = tentative(Duration.ofMinutes(61));
        prestataireLit(StatutPaiement.EXPIRE, false);

        job.annulerLesCommandesExpirees();

        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.EXPIRE);
        assertAnnuleePourDelaiDepasse();
    }

    @Test
    @DisplayName("prestataire injoignable : rien n'est décidé, le passage suivant relira")
    void panne() {
        Paiement paiement = tentative(Duration.ofMinutes(20));
        when(prestataire.lireEtat(REFERENCE))
                .thenThrow(new PrestataireIndisponibleException("panne"));

        job.annulerLesCommandesExpirees();

        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.INITIE);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        verify(commandes, never()).verrouillerParId(any());
    }

    @Test
    @DisplayName("annulation refusée par le prestataire : rien n'est annulé de notre côté")
    void annulationRefusee() {
        Paiement paiement = tentative(Duration.ofMinutes(20));
        prestataireLit(StatutPaiement.INITIE, true);
        // Réglé entre la lecture et l'annulation : le prestataire refuse.
        when(prestataire.annuler(REFERENCE))
                .thenThrow(new PrestataireIndisponibleException("refus"));

        job.annulerLesCommandesExpirees();

        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.INITIE);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
    }

    @Test
    @DisplayName("sans statut définitif après deux heures : une alerte, une seule, et rien d'autre")
    void alerteApresDeuxHeures(CapturedOutput sortie) {
        tentative(Duration.ofMinutes(121));
        prestataireLit(StatutPaiement.INITIE, false);

        job.annulerLesCommandesExpirees();
        job.annulerLesCommandesExpirees();

        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(sortie.getOut().split("aucun statut définitif", -1)).hasSize(2);
        assertThat(sortie.getOut()).contains(REFERENCE);
    }

    @Test
    @DisplayName("attente de moins de deux heures : aucune alerte")
    void pasDAlerteAvantLePlafond(CapturedOutput sortie) {
        tentative(Duration.ofMinutes(119));
        prestataireLit(StatutPaiement.INITIE, false);

        job.annulerLesCommandesExpirees();

        assertThat(sortie.getOut()).doesNotContain("aucun statut définitif");
    }

    /**
     * Course job / webhook : la commande attendait au balayage, mais une notification
     * « payé » a été validée avant le verrou. La relecture sous verrou voit PAYEE et le
     * job passe son chemin : une PAYEE ne redevient jamais ANNULEE.
     */
    @Test
    @DisplayName("payée entre la lecture et le verrou : le job passe, aucun état incohérent")
    void payeeEntreLectureEtVerrou() {
        Commande payee = new Commande("CMD-2026-0001", commande.getMembre(),
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                MAINTENANT.minus(Duration.ofMinutes(31)));
        payee.confirmerPaiement(MAINTENANT);
        when(commandes.verrouillerParId(10L)).thenReturn(Optional.of(payee));

        job.annulerLesCommandesExpirees();

        assertThat(payee.getStatut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(commande.getMotifAnnulation()).isNull();
        verify(commandes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("tentative ouverte entre la lecture et le verrou : la commande n'est pas annulée")
    void tentativeNeeEntreTemps() {
        // Aucune tentative à la lecture, une à la revérification sous verrou.
        when(paiements.findByCommandeAndStatutIn(eq(commande), anyCollection()))
                .thenReturn(List.of())
                .thenAnswer(appel -> {
                    Paiement nouvelle = new Paiement(commande, commande.getMontantTvac(), MAINTENANT);
                    nouvelle.enregistrerReferencePrestataire("tr_fictif_0002");
                    return List.of(nouvelle);
                });

        job.annulerLesCommandesExpirees();

        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
    }

    @Test
    @DisplayName("aucune candidate : le job ne touche à rien")
    void rienAExpirer() {
        when(commandes.parStatutAnterieuresA(eq(StatutCommande.EN_ATTENTE_PAIEMENT), any()))
                .thenReturn(List.of());

        job.annulerLesCommandesExpirees();

        verify(commandes, never()).verrouillerParId(any());
        verify(commandes, never()).saveAndFlush(any());
    }
}
