package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.communication.service.DetailsInterventionTerminee;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Contrat transactionnel de la notification de cloture (F17) contre un vrai
 * PostgreSQL : le courriel part APRES le commit de {@code terminer}, jamais dans
 * une transaction rollbackee.
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} de classe, contrairement aux
 * autres IT : envelopper les tests dans une transaction rollbackee empecherait tout
 * commit, et le listener {@code AFTER_COMMIT} ne se declencherait jamais — le contrat
 * teste est precisement le commit. Consequence : les donnees inserees restent en base
 * du conteneur (jetable, un par classe), d ou les fixtures a suffixe unique.</p>
 */
@SpringBootTest
@Testcontainers
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Notification de cloture d'intervention (integration)")
class NotificationInterventionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Numeros de RDV et creneaux uniques : sans rollback, chaque test laisse ses donnees. */
    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private InterventionService service;
    @Autowired private InterventionRepository interventions;
    @Autowired private GenerateurNumeroIntervention numeros;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private TransactionTemplate transactions;

    @MockitoBean private ServiceCourriel courriel;

    @BeforeEach
    void setUp() {
        // Fixtures partagees, creees au premier test puis reutilisees : sans rollback,
        // une recreation violerait les contraintes d unicite (email, plaque).
        transactions.executeWithoutResult(statut -> {
            Utilisateur marie = utilisateurs.findByEmailIgnoreCase("marie@exemple.be")
                    .orElseGet(() -> utilisateurs.save(new Utilisateur(
                            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE)));
            if (vehicules.findAll().stream().noneMatch(v -> "1-ABC-123".equals(v.getPlaque()))) {
                vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
            }
        });
    }

    /**
     * Un RDV sur un creneau unique (contrainte d exclusion) et son intervention
     * EN_COURS. La fixture est construite DANS une transaction : le constructeur de
     * {@code Rdv} verifie RM-06 via {@code vehicule.appartientA(membre)}, et le membre
     * d un vehicule recharge est un proxy lazy — hors session, il ne s initialise pas.
     * La transaction est committee avant le test : c est le commit de {@code terminer},
     * pas celui-ci, que le test observe.
     */
    private UUID interventionEnCours() {
        UUID ref = transactions.execute(statut -> {
            int n = COMPTEUR.getAndIncrement();
            Utilisateur marie = utilisateurs.findByEmailIgnoreCase("marie@exemple.be").orElseThrow();
            Vehicule golf = vehicules.findAll().stream()
                    .filter(v -> "1-ABC-123".equals(v.getPlaque()))
                    .findFirst().orElseThrow();
            // Catalogue et postes seedes par V16/V17 : on reutilise plutot que dupliquer.
            Prestation prestation = prestations.findByActifTrueOrderByLibelleAsc().get(0);
            PosteAtelier poste = postes.findAll().get(0);
            Rdv rdv = rdvs.saveAndFlush(new Rdv("RDV-IT-NOTIF-%04d".formatted(n), marie, golf, poste,
                    Instant.parse("2026-12-01T06:00:00Z").plus(Duration.ofHours(n)),
                    Duration.ofMinutes(30), List.of(prestation), null));
            return interventions.saveAndFlush(new Intervention(numeros.prochain(), rdv)).getReference();
        });
        service.demarrer(ref);
        return ref;
    }

    @Test
    @DisplayName("apres commit de terminer, le courriel part avec les donnees du membre")
    void courrielApresCommit() {
        UUID ref = interventionEnCours();

        service.terminer(ref);

        // Le listener AFTER_COMMIT s execute de maniere synchrone au commit : la
        // verification peut suivre immediatement l appel.
        ArgumentCaptor<DetailsInterventionTerminee> captor =
                ArgumentCaptor.forClass(DetailsInterventionTerminee.class);
        verify(courriel).envoyerInterventionTerminee(captor.capture());
        DetailsInterventionTerminee details = captor.getValue();
        assertThat(details.adresseEmail()).isEqualTo("marie@exemple.be");
        assertThat(details.prenom()).isEqualTo("Marie");
        assertThat(details.libelleVehicule()).isEqualTo("VW Golf");
        assertThat(details.immatriculation()).isEqualTo("1-ABC-123");
        assertThat(interventions.findByReference(ref).orElseThrow().getStatut())
                .isEqualTo(StatutIntervention.TERMINEE);
    }

    @Test
    @DisplayName("transaction rollbackee : aucun courriel, l'evenement meurt avec la transaction")
    void rollbackSansCourriel() {
        UUID ref = interventionEnCours();

        transactions.executeWithoutResult(statut -> {
            service.terminer(ref);
            statut.setRollbackOnly();
        });

        verify(courriel, never()).envoyerInterventionTerminee(any());
        assertThat(interventions.findByReference(ref).orElseThrow().getStatut())
                .as("Le rollback doit avoir efface la transition elle-meme")
                .isEqualTo(StatutIntervention.EN_COURS);
    }

    @Test
    @DisplayName("fournisseur mail en panne : la cloture committee ne retombe pas en erreur")
    void echecCourrielSansEffetSurLaCloture() {
        UUID ref = interventionEnCours();
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(courriel).envoyerInterventionTerminee(any());

        assertThatCode(() -> service.terminer(ref)).doesNotThrowAnyException();

        assertThat(interventions.findByReference(ref).orElseThrow().getStatut())
                .isEqualTo(StatutIntervention.TERMINEE);
    }
}
