package be.autoservplus.avis.service;

import be.autoservplus.avis.domain.Avis;
import be.autoservplus.avis.repository.AvisRepository;
import be.autoservplus.avis.service.dto.SyntheseAvis;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AvisService (BL-4)")
class AvisServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-23T08:00:00Z");
    private static final UUID REFERENCE = UUID.randomUUID();
    private static final String MARIE = "marie@exemple.be";
    private static final String PAUL = "paul@exemple.be";

    @Mock private AvisRepository avis;
    @Mock private InterventionRepository interventions;
    @Mock private UtilisateurRepository membres;
    @Mock private ParametreAtelierRepository parametres;
    @Mock private NotificationService notifications;

    private AvisService service;
    private Utilisateur marie;
    private Utilisateur patron;

    @BeforeEach
    void setUp() {
        marie = new Utilisateur(MARIE, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        patron = new Utilisateur("patron@autoservplus.be", "$2a$12$h", "Garage", "Patron",
                TypeUtilisateur.ADMINISTRATEUR);

        when(membres.findByEmailIgnoreCase(MARIE)).thenReturn(Optional.of(marie));
        when(parametres.courants()).thenReturn(new ParametreAtelier());
        when(membres.findByTypeUtilisateurAndStatut(
                TypeUtilisateur.ADMINISTRATEUR, StatutUtilisateur.ACTIF))
                .thenReturn(List.of(patron));
        when(avis.save(any(Avis.class))).thenAnswer(appel -> appel.getArgument(0));

        service = new AvisService(avis, interventions, membres, parametres, notifications,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    private Intervention interventionDe(String emailProprietaire, StatutIntervention statut) {
        Utilisateur proprietaire = emailProprietaire.equals(MARIE) ? marie
                : new Utilisateur(emailProprietaire, "$2a$12$h", "Martin", "Paul",
                        TypeUtilisateur.MEMBRE);
        Rdv rdv = org.mockito.Mockito.mock(Rdv.class);
        when(rdv.getMembre()).thenReturn(proprietaire);
        Intervention intervention = org.mockito.Mockito.mock(Intervention.class);
        when(intervention.getRdv()).thenReturn(rdv);
        when(intervention.getStatut()).thenReturn(statut);
        when(intervention.getNumero()).thenReturn("ITV-2026-0011");
        when(interventions.findByReference(REFERENCE)).thenReturn(Optional.of(intervention));
        return intervention;
    }

    @Nested
    @DisplayName("Eligibilite au depot")
    class Eligibilite {

        @Test
        @DisplayName("le titulaire d une intervention terminee non notee peut deposer")
        void casNominal() {
            interventionDe(MARIE, StatutIntervention.TERMINEE);
            when(avis.existsByIntervention(any())).thenReturn(false);

            assertThat(service.peutDeposer(MARIE, REFERENCE)).isTrue();
        }

        @Test
        @DisplayName("ne peut pas deposer deux fois sur la meme intervention")
        void dejaNotee() {
            interventionDe(MARIE, StatutIntervention.TERMINEE);
            when(avis.existsByIntervention(any())).thenReturn(true);

            assertThat(service.peutDeposer(MARIE, REFERENCE)).isFalse();
            assertThatThrownBy(() -> service.interventionNotable(MARIE, REFERENCE))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("déjà");
        }

        @Test
        @DisplayName("ne peut pas noter des travaux non termines")
        void travauxEnCours() {
            interventionDe(MARIE, StatutIntervention.EN_COURS);

            assertThat(service.peutDeposer(MARIE, REFERENCE)).isFalse();
            assertThatThrownBy(() -> service.interventionNotable(MARIE, REFERENCE))
                    .isInstanceOf(RegleMetierException.class);
        }

        @Test
        @DisplayName("l intervention d autrui remonte en 404, pas en 403")
        void interventionDAutrui() {
            interventionDe(PAUL, StatutIntervention.TERMINEE);

            assertThat(service.peutDeposer(MARIE, REFERENCE)).isFalse();
            assertThatThrownBy(() -> service.interventionNotable(MARIE, REFERENCE))
                    .as("repondre interdit confirmerait que la reference existe")
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("une reference inconnue remonte le meme 404")
        void referenceInconnue() {
            when(interventions.findByReference(REFERENCE)).thenReturn(Optional.empty());

            assertThat(service.peutDeposer(MARIE, REFERENCE)).isFalse();
            assertThatThrownBy(() -> service.interventionNotable(MARIE, REFERENCE))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("Depot")
    class Depot {

        @Test
        @DisplayName("enregistre l avis et previent le garage")
        void depotNominal() {
            interventionDe(MARIE, StatutIntervention.TERMINEE);
            when(avis.existsByIntervention(any())).thenReturn(false);

            Avis depose = service.deposer(MARIE, REFERENCE, (short) 5, "Impeccable.");

            assertThat(depose.getNote()).isEqualTo((short) 5);
            assertThat(depose.getMembre()).isEqualTo(marie);
            verify(notifications).deposer(patron, TypeNotification.AVIS_DEPOSE, "ITV-2026-0011");
        }

        @Test
        @DisplayName("sans administrateur actif, le depot aboutit quand meme")
        void aucunAdministrateur() {
            interventionDe(MARIE, StatutIntervention.TERMINEE);
            when(avis.existsByIntervention(any())).thenReturn(false);
            when(membres.findByTypeUtilisateurAndStatut(
                    TypeUtilisateur.ADMINISTRATEUR, StatutUtilisateur.ACTIF))
                    .thenReturn(List.of());

            assertThat(service.deposer(MARIE, REFERENCE, (short) 4, null)).isNotNull();
            verify(notifications, never()).deposer(any(), any(), anyString());
        }

        @Test
        @DisplayName("un depot refuse n ecrit rien et ne notifie personne")
        void depotRefuse() {
            interventionDe(MARIE, StatutIntervention.EN_COURS);

            assertThatThrownBy(() -> service.deposer(MARIE, REFERENCE, (short) 5, null))
                    .isInstanceOf(RegleMetierException.class);
            verify(avis, never()).save(any());
            verify(notifications, never()).deposer(any(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("Synthese publique")
    class Synthese {

        @Test
        @DisplayName("une prestation sans avis n est pas notee zero")
        void sansAvis() {
            when(avis.syntheseParPrestation(REFERENCE)).thenReturn(null);

            SyntheseAvis synthese = service.synthese(REFERENCE);

            assertThat(synthese.aDesAvis()).isFalse();
            assertThat(synthese.moyenneAffichee())
                    .as("le CHECK interdit la note zero : une moyenne nulle ne peut pas etre un jugement")
                    .isNull();
        }

        @Test
        @DisplayName("arrondit la moyenne a une decimale")
        void moyenneArrondie() {
            when(avis.syntheseParPrestation(REFERENCE))
                    .thenReturn(new SyntheseAvis(4.26, 3L));

            assertThat(service.synthese(REFERENCE).moyenneAffichee())
                    .hasToString("4.3");
        }
    }
}
