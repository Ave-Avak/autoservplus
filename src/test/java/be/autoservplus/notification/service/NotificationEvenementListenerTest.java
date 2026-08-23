package be.autoservplus.notification.service;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.InterventionTermineeEvent;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.RdvStatutModifieEvent;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.retractation.service.DecisionRetractationEvent;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.service.CommandePayeeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du routage evenement -> notification (BL-6).
 *
 * <p>Les entites sont mockees : ce test ne verifie pas leur comportement, deja couvert
 * ailleurs, mais le <b>choix du libelle et du destinataire</b> a partir de l etat
 * committe, et le fait qu aucune exception ne s echappe.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationEvenementListener (BL-6)")
class NotificationEvenementListenerTest {

    private static final UUID REFERENCE = UUID.randomUUID();

    @Mock private NotificationService notifications;
    @Mock private RdvRepository rdvs;
    @Mock private CommandeRepository commandes;
    @Mock private InterventionRepository interventions;
    @Mock private DemandeAnnulationRepository demandes;

    private NotificationEvenementListener listener;
    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        listener = new NotificationEvenementListener(notifications, rdvs, commandes,
                interventions, demandes);
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    @Nested
    @DisplayName("Rendez-vous")
    class RendezVous {

        @ParameterizedTest(name = "{0} donne {1}")
        @CsvSource({
                "CONFIRME, RDV_CONFIRME",
                "REFUSE,   RDV_REFUSE",
                "ANNULE,   RDV_ANNULE",
                "HONORE,   RDV_HONORE",
                "ABSENT,   RDV_ABSENT"
        })
        @DisplayName("chaque transition du garage produit son libelle")
        void chaqueTransition(StatutRdv statut, TypeNotification attendu) {
            Rdv rdv = mock(Rdv.class);
            when(rdv.getStatut()).thenReturn(statut);
            when(rdv.getMembre()).thenReturn(marie);
            when(rdv.getNumero()).thenReturn("RDV-2026-0007");
            when(rdvs.findByReference(REFERENCE)).thenReturn(Optional.of(rdv));

            listener.surStatutRdv(new RdvStatutModifieEvent(REFERENCE));

            verify(notifications).deposer(marie, attendu, "RDV-2026-0007");
        }

        @Test
        @DisplayName("EN_ATTENTE ne notifie rien : le membre vient de deposer sa demande")
        void enAttenteSilencieux() {
            Rdv rdv = mock(Rdv.class);
            when(rdv.getStatut()).thenReturn(StatutRdv.EN_ATTENTE);
            when(rdvs.findByReference(REFERENCE)).thenReturn(Optional.of(rdv));

            listener.surStatutRdv(new RdvStatutModifieEvent(REFERENCE));

            verify(notifications, never()).deposer(any(), any(), anyString());
        }

        @Test
        @DisplayName("un rendez-vous introuvable apres commit ne fait pas echouer le listener")
        void rdvIntrouvable() {
            when(rdvs.findByReference(REFERENCE)).thenReturn(Optional.empty());

            assertThatCode(() -> listener.surStatutRdv(new RdvStatutModifieEvent(REFERENCE)))
                    .doesNotThrowAnyException();
            verify(notifications, never()).deposer(any(), any(), anyString());
        }

        @Test
        @DisplayName("une panne du depot est avalee : la transition est deja committee")
        void pannePendantLeDepot() {
            Rdv rdv = mock(Rdv.class);
            when(rdv.getStatut()).thenReturn(StatutRdv.CONFIRME);
            when(rdv.getMembre()).thenReturn(marie);
            when(rdv.getNumero()).thenReturn("RDV-2026-0007");
            when(rdvs.findByReference(REFERENCE)).thenReturn(Optional.of(rdv));
            doThrow(new IllegalStateException("base indisponible"))
                    .when(notifications).deposer(any(), any(), anyString());

            assertThatCode(() -> listener.surStatutRdv(new RdvStatutModifieEvent(REFERENCE)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Commande, intervention et retractation")
    class AutresEvenements {

        @Test
        @DisplayName("une commande payee notifie son titulaire")
        void commandePayee() {
            Commande commande = mock(Commande.class);
            when(commande.getMembre()).thenReturn(marie);
            when(commande.getNumero()).thenReturn("CMD-2026-0003");
            when(commandes.findByReference(REFERENCE)).thenReturn(Optional.of(commande));

            listener.surCommandePayee(new CommandePayeeEvent(REFERENCE));

            verify(notifications).deposer(marie, TypeNotification.COMMANDE_PAYEE, "CMD-2026-0003");
        }

        @Test
        @DisplayName("une intervention terminee notifie le membre du rendez-vous")
        void interventionTerminee() {
            Rdv rdv = mock(Rdv.class);
            when(rdv.getMembre()).thenReturn(marie);
            Intervention intervention = mock(Intervention.class);
            when(intervention.getRdv()).thenReturn(rdv);
            when(intervention.getNumero()).thenReturn("ITV-2026-0011");
            when(interventions.findByReference(REFERENCE)).thenReturn(Optional.of(intervention));

            listener.surInterventionTerminee(new InterventionTermineeEvent(REFERENCE));

            verify(notifications).deposer(marie, TypeNotification.INTERVENTION_TERMINEE,
                    "ITV-2026-0011");
        }

        @Test
        @DisplayName("une retractation refusee porte le numero de commande, pas un avoir")
        void retractationRefusee() {
            Commande commande = mock(Commande.class);
            when(commande.getMembre()).thenReturn(marie);
            when(commande.getNumero()).thenReturn("CMD-2026-0003");
            DemandeAnnulation demande = mock(DemandeAnnulation.class);
            when(demande.getCommande()).thenReturn(commande);
            when(demande.getStatut()).thenReturn(StatutDemandeAnnulation.REFUSEE);
            when(demandes.findByReference(REFERENCE)).thenReturn(Optional.of(demande));

            listener.surDecisionRetractation(new DecisionRetractationEvent(REFERENCE));

            verify(notifications).deposer(marie, TypeNotification.RETRACTATION_REFUSEE,
                    "CMD-2026-0003");
        }

        @Test
        @DisplayName("une demande encore en attente ne notifie rien")
        void demandeEncoreEnAttente() {
            Commande commande = mock(Commande.class);
            when(commande.getMembre()).thenReturn(marie);
            DemandeAnnulation demande = mock(DemandeAnnulation.class);
            when(demande.getCommande()).thenReturn(commande);
            when(demande.getStatut()).thenReturn(StatutDemandeAnnulation.EN_ATTENTE);
            when(demandes.findByReference(REFERENCE)).thenReturn(Optional.of(demande));

            listener.surDecisionRetractation(new DecisionRetractationEvent(REFERENCE));

            verify(notifications, never()).deposer(any(), any(), anyString());
        }
    }
}
