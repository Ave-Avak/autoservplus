package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.communication.service.DetailsInterventionTerminee;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
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
@DisplayName("NotificationInterventionListener")
class NotificationInterventionListenerTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    @Mock private InterventionRepository interventions;
    @Mock private ServiceCourriel courriel;

    private NotificationInterventionListener listener;
    private Intervention intervention;

    @BeforeEach
    void setUp() {
        listener = new NotificationInterventionListener(interventions, courriel);

        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
        Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
        Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
        Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, new PosteAtelier("Pont 1"), MAINTENANT,
                Duration.ofMinutes(30), List.of(vidange), null);
        intervention = new Intervention("INT-2026-0001", rdv);
    }

    @Test
    @DisplayName("recharge l'intervention et envoie le courriel avec les donnees du membre")
    void envoieLeCourrielDeCloture() {
        UUID ref = intervention.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(intervention));

        listener.surInterventionTerminee(new InterventionTermineeEvent(ref));

        ArgumentCaptor<DetailsInterventionTerminee> captor =
                ArgumentCaptor.forClass(DetailsInterventionTerminee.class);
        verify(courriel).envoyerInterventionTerminee(captor.capture());
        DetailsInterventionTerminee details = captor.getValue();
        assertThat(details.adresseEmail()).isEqualTo("marie@exemple.be");
        assertThat(details.prenom()).isEqualTo("Marie");
        assertThat(details.numeroIntervention()).isEqualTo("INT-2026-0001");
        assertThat(details.libelleVehicule()).isEqualTo("VW Golf");
        assertThat(details.immatriculation()).isEqualTo("1-ABC-123");
    }

    @Test
    @DisplayName("reference introuvable apres commit : journalise, aucun courriel, aucune exception")
    void referenceIntrouvableAbsorbee() {
        UUID ref = UUID.randomUUID();
        when(interventions.findByReference(ref)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.surInterventionTerminee(new InterventionTermineeEvent(ref)))
                .doesNotThrowAnyException();

        verifyNoInteractions(courriel);
    }

    @Test
    @DisplayName("intervention sans RDV lie (entree directe) : aucun membre a prevenir")
    void sansRdvAucunCourriel() {
        // Le constructeur V1 exige un RDV : l entree directe (hors V1) n existe qu en
        // base. On simule ce cas avec un mock — c est la branche, pas l entite, qu on teste.
        Intervention sansRdv = mock(Intervention.class);
        when(sansRdv.getRdv()).thenReturn(null);
        UUID ref = UUID.randomUUID();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(sansRdv));

        assertThatCode(() -> listener.surInterventionTerminee(new InterventionTermineeEvent(ref)))
                .doesNotThrowAnyException();

        verifyNoInteractions(courriel);
    }

    @Test
    @DisplayName("une exception du fournisseur mail est avalee : la cloture committee ne retombe pas en erreur")
    void exceptionDuCourrielAbsorbee() {
        UUID ref = intervention.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(intervention));
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(courriel).envoyerInterventionTerminee(any());

        assertThatCode(() -> listener.surInterventionTerminee(new InterventionTermineeEvent(ref)))
                .doesNotThrowAnyException();
    }
}
