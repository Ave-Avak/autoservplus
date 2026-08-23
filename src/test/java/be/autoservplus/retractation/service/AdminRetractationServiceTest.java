package be.autoservplus.retractation.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.AvoirService;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.DemandeRemboursement;
import be.autoservplus.vente.service.PrestatairePaiement;
import be.autoservplus.vente.service.RemboursementCree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision du garage sur une retractation (F30) : la validation contre-passe,
 * rembourse et bascule les etats dans une seule transaction ; le refus n ecrit
 * aucun mouvement comptable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRetractationService")
class AdminRetractationServiceTest {

    private static final Instant COMMANDE_LE = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant MAINTENANT = Instant.parse("2026-09-04T10:00:00Z");
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Mock private DemandeAnnulationRepository demandes;
    @Mock private CommandeRepository commandes;
    @Mock private PaiementRepository paiements;
    @Mock private FactureRepository factures;
    @Mock private AvoirService avoirs;
    @Mock private PrestatairePaiement prestataire;
    @Mock private UtilisateurRepository utilisateurs;
    @Mock private ApplicationEventPublisher evenements;

    private AdminRetractationService service;

    private Utilisateur marie;
    private Utilisateur admin;
    private Commande commande;
    private Paiement paiement;
    private Facture facture;
    private DemandeAnnulation demande;

    @BeforeEach
    void setUp() {
        service = new AdminRetractationService(demandes, commandes, paiements, factures,
                avoirs, prestataire, utilisateurs, evenements,
                Clock.fixed(MAINTENANT, BRUXELLES));

        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        admin = new Utilisateur("admin@autoservplus.be", "$2a$12$h", "Garage", "Admin",
                TypeUtilisateur.ADMINISTRATEUR);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                COMMANDE_LE);
        commande.confirmerPaiement(COMMANDE_LE);
        paiement = new Paiement(commande, commande.getMontantTvac(), COMMANDE_LE);
        paiement.enregistrerReferencePrestataire("tr_fictif_0001");
        paiement.confirmer(COMMANDE_LE);
        facture = Facture.pourCommande("2026-0042", (short) 2026, 42, commande,
                new BigDecimal("21.00"), COMMANDE_LE);
        demande = new DemandeAnnulation(commande, "trop cher",
                MAINTENANT.minus(Duration.ofDays(1)));
    }

    private void demandeChargeable() {
        when(demandes.findByReference(demande.getReference())).thenReturn(Optional.of(demande));
        when(utilisateurs.findByEmailIgnoreCase("admin@autoservplus.be"))
                .thenReturn(Optional.of(admin));
    }

    private Avoir cheminNominal() {
        demandeChargeable();
        when(commandes.verrouillerParId(commande.getId())).thenReturn(Optional.of(commande));
        when(factures.findByCommande(commande)).thenReturn(Optional.of(facture));
        when(paiements.findByCommandeAndStatutIn(commande, List.of(StatutPaiement.REUSSI)))
                .thenReturn(List.of(paiement));
        Avoir avoir = Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, MAINTENANT);
        when(avoirs.contrePasser(facture, Avoir.MOTIF_RETRACTATION)).thenReturn(avoir);
        return avoir;
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("emet l'avoir, rembourse, et bascule commande, paiement et demande")
        void cheminComplet() {
            Avoir avoir = cheminNominal();
            when(prestataire.rembourser(any(DemandeRemboursement.class)))
                    .thenReturn(new RemboursementCree("re_fictif_0001"));

            DemandeAnnulation validee = service.valider(
                    demande.getReference(), "admin@autoservplus.be");

            assertThat(validee.getStatut()).isEqualTo(StatutDemandeAnnulation.VALIDEE);
            assertThat(validee.getAvoir()).isSameAs(avoir);
            assertThat(validee.getDecidePar()).isSameAs(admin);
            assertThat(validee.getDecideLe()).isEqualTo(MAINTENANT);

            assertThat(commande.getStatut()).isEqualTo(StatutCommande.REMBOURSEE);
            assertThat(commande.getMotifAnnulation())
                    .isEqualTo(MotifAnnulationCommande.RETRACTATION_F30);
            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REMBOURSE);
            assertThat(paiement.getReferenceRemboursement()).isEqualTo("re_fictif_0001");

            verify(evenements).publishEvent(any(DecisionRetractationEvent.class));
        }

        @Test
        @DisplayName("la demande de remboursement porte le paiement, son montant et une cle stable")
        void demandeDeRemboursement() {
            cheminNominal();
            when(prestataire.rembourser(any(DemandeRemboursement.class)))
                    .thenReturn(new RemboursementCree("re_fictif_0001"));

            service.valider(demande.getReference(), "admin@autoservplus.be");

            ArgumentCaptor<DemandeRemboursement> capture =
                    ArgumentCaptor.forClass(DemandeRemboursement.class);
            verify(prestataire).rembourser(capture.capture());
            DemandeRemboursement envoyee = capture.getValue();
            assertThat(envoyee.referencePrestataire()).isEqualTo("tr_fictif_0001");
            assertThat(envoyee.montantTvac()).isEqualByComparingTo("48.38");
            assertThat(envoyee.devise()).isEqualTo("EUR");
            // Cle derivee du paiement : un rejeu envoie la meme, le prestataire ne
            // rembourse pas deux fois.
            assertThat(envoyee.cleIdempotence())
                    .isEqualTo(paiement.cleIdempotenceRemboursement());
        }

        @Test
        @DisplayName("le prestataire est appele en DERNIER : un echec n'emet ni avoir ni remboursement acquis")
        void appelExterneEnDernier() {
            cheminNominal();
            when(prestataire.rembourser(any(DemandeRemboursement.class)))
                    .thenThrow(new IllegalStateException("prestataire indisponible"));

            assertThatThrownBy(() -> service.valider(
                    demande.getReference(), "admin@autoservplus.be"))
                    .isInstanceOf(IllegalStateException.class);

            // L exception remonte : la transaction est annulee, donc l avoir et son
            // numero avec elle. Le paiement, lui, n a jamais bascule.
            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REUSSI);
            verify(evenements, never()).publishEvent(any(DecisionRetractationEvent.class));
        }

        @Test
        @DisplayName("sans facture, rien n'est contre-passe ni rembourse")
        void sansFacture() {
            demandeChargeable();
            when(commandes.verrouillerParId(commande.getId())).thenReturn(Optional.of(commande));
            when(factures.findByCommande(commande)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.valider(
                    demande.getReference(), "admin@autoservplus.be"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-23");
            verify(prestataire, never()).rembourser(any());
            verify(avoirs, never()).contrePasser(any(), any());
        }

        @Test
        @DisplayName("sans paiement encaisse, l'incoherence remonte au lieu d'etre contournee")
        void sansPaiementEncaisse() {
            demandeChargeable();
            when(commandes.verrouillerParId(commande.getId())).thenReturn(Optional.of(commande));
            when(factures.findByCommande(commande)).thenReturn(Optional.of(facture));
            when(paiements.findByCommandeAndStatutIn(commande, List.of(StatutPaiement.REUSSI)))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.valider(
                    demande.getReference(), "admin@autoservplus.be"))
                    .isInstanceOf(RegleMetierException.class);
            verify(avoirs, never()).contrePasser(any(), any());
        }

        @Test
        @DisplayName("une demande deja tranchee echoue AVANT tout effet de bord")
        void demandeDejaTranchee() {
            demande.refuser("Piece deja montee", admin, MAINTENANT.minusSeconds(60));
            when(demandes.findByReference(demande.getReference()))
                    .thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> service.valider(
                    demande.getReference(), "admin@autoservplus.be"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REFUSEE");

            // Rien n a ete lu ni ecrit : sans ce fail-fast, un second clic irait
            // jusqu au remboursement pour echouer sur « aucun paiement encaisse »,
            // ce qui est vrai mais trompeur — le paiement est deja REMBOURSE.
            verify(commandes, never()).verrouillerParId(any());
            verify(factures, never()).findByCommande(any());
            verify(avoirs, never()).contrePasser(any(), any());
            verify(prestataire, never()).rembourser(any());
        }

        @Test
        @DisplayName("un second clic apres validation ne rembourse pas une seconde fois")
        void doubleClicApresValidation() {
            cheminNominal();
            when(prestataire.rembourser(any(DemandeRemboursement.class)))
                    .thenReturn(new RemboursementCree("re_fictif_0001"));
            service.valider(demande.getReference(), "admin@autoservplus.be");

            assertThatThrownBy(() -> service.valider(
                    demande.getReference(), "admin@autoservplus.be"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VALIDEE");

            // Un seul appel au prestataire, malgre deux validations demandees.
            verify(prestataire).rembourser(any(DemandeRemboursement.class));
        }

        @Test
        @DisplayName("une reference inconnue remonte comme introuvable")
        void referenceInconnue() {
            when(demandes.findByReference(demande.getReference())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.valider(
                    demande.getReference(), "admin@autoservplus.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("refus")
    class Refus {

        @Test
        @DisplayName("trace la decision sans emettre d'avoir ni rembourser")
        void refusMotive() {
            demandeChargeable();

            DemandeAnnulation refusee = service.refuser(demande.getReference(),
                    "Piece deja montee sur le vehicule", "admin@autoservplus.be");

            assertThat(refusee.getStatut()).isEqualTo(StatutDemandeAnnulation.REFUSEE);
            assertThat(refusee.getMotifDecision()).isEqualTo("Piece deja montee sur le vehicule");
            assertThat(refusee.getDecidePar()).isSameAs(admin);
            // Aucun mouvement comptable : la facture reste seule au dossier.
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REUSSI);
            verify(prestataire, never()).rembourser(any());
            verify(avoirs, never()).contrePasser(any(), any());
            // Le membre est prevenu du refus comme il l aurait ete d une acceptation.
            verify(evenements).publishEvent(any(DecisionRetractationEvent.class));
        }

        @Test
        @DisplayName("un refus sans motif est rejete par l'entite")
        void refusSansMotif() {
            demandeChargeable();

            assertThatThrownBy(() -> service.refuser(
                    demande.getReference(), "   ", "admin@autoservplus.be"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
            verify(evenements, never()).publishEvent(any(DecisionRetractationEvent.class));
        }
    }
}
