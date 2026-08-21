package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterventionService")
class InterventionServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    @Mock private InterventionRepository interventions;
    @Mock private PrestationRepository prestations;
    @Mock private ParametreAtelierRepository parametres;
    @Mock private GenerateurNumeroIntervention numeros;

    private Clock horloge;
    private InterventionService service;

    private Utilisateur marie;
    private Vehicule golf;
    private PosteAtelier pont;
    private Prestation vidange;
    private Rdv rdv;

    @BeforeEach
    void setUp() {
        horloge = Clock.fixed(MAINTENANT, BRUXELLES);
        service = new InterventionService(interventions, prestations, parametres, numeros, horloge);

        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
        pont = new PosteAtelier("Pont 1");
        Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
        vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
        rdv = new Rdv("RDV-2026-0001", marie, golf, pont, MAINTENANT,
                Duration.ofMinutes(30), List.of(vidange), null);
    }

    @Test
    @DisplayName("creerDepuisRdv cree une nouvelle intervention quand aucune n'existe")
    void creationNouvelle() {
        when(interventions.findByRdvId(any())).thenReturn(Optional.empty());
        when(numeros.prochain()).thenReturn("INT-2026-0001");
        when(interventions.saveAndFlush(any(Intervention.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Intervention creee = service.creerDepuisRdv(rdv);

        assertThat(creee.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(creee.getNumero()).isEqualTo("INT-2026-0001");
        assertThat(creee.getLignes()).hasSize(1);
        verify(interventions).saveAndFlush(any(Intervention.class));
    }

    @Test
    @DisplayName("creerDepuisRdv est idempotent : retourne l'existante sans doublon")
    void idempotence() {
        Intervention existante = new Intervention("INT-2026-0001", rdv);
        when(interventions.findByRdvId(any())).thenReturn(Optional.of(existante));

        Intervention resultat = service.creerDepuisRdv(rdv);

        assertThat(resultat).isSameAs(existante);
        verify(interventions, never()).saveAndFlush(any());
        verify(numeros, never()).prochain();
    }

    @Test
    @DisplayName("demarrer passe en EN_COURS et sauvegarde")
    void demarrer() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        when(interventions.saveAndFlush(it)).thenReturn(it);

        Intervention resultat = service.demarrer(ref);

        assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(resultat.getDebutReel()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("reference absente -> RessourceIntrouvableException")
    void referenceAbsente() {
        UUID ref = UUID.randomUUID();
        when(interventions.findByReference(ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.demarrer(ref))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("OptimisticLockingFailureException -> ConflitConcurrenceException")
    void traduitConflitConcurrence() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        doThrow(new OptimisticLockingFailureException("stale"))
                .when(interventions).saveAndFlush(it);

        assertThatThrownBy(() -> service.demarrer(ref))
                .isInstanceOf(ConflitConcurrenceException.class)
                .hasMessageContaining("rechargez");
    }

    @Test
    @DisplayName("suspendre : EN_COURS -> SUSPENDUE et sauvegarde")
    void suspendre() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        it.demarrer(MAINTENANT);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        when(interventions.saveAndFlush(it)).thenReturn(it);

        Intervention resultat = service.suspendre(ref);

        assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.SUSPENDUE);
    }

    @Test
    @DisplayName("annuler : bascule en ANNULEE et sauvegarde")
    void annuler() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        when(interventions.saveAndFlush(it)).thenReturn(it);

        Intervention resultat = service.annuler(ref);

        assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.ANNULEE);
    }

    @Test
    @DisplayName("modifierCommentaireAdmin trim et persiste")
    void commentaire() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        doReturn(Optional.of(it)).when(interventions).findByReference(ref);
        doReturn(it).when(interventions).saveAndFlush(it);

        Intervention resultat = service.modifierCommentaireAdmin(ref, "  Piece commandee ");

        assertThat(resultat.getCommentaireAdmin()).isEqualTo("Piece commandee");
    }
}
