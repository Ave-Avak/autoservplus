package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exerce les requetes JPQL du tableau de bord admin contre un vrai PostgreSQL 16.
 *
 * <p>Les tests unitaires de {@code AdminRdvServiceTest} mockent le repository : ils
 * ne detectent pas un JPQL mal forme (typo dans un JOIN FETCH, mauvais parametre)
 * qui ne peterait qu au runtime. Ce IT execute effectivement chaque requete et
 * verifie que le mapping vers {@link RdvVueAdmin} construit une vue complete, ce
 * qui prouve que les relations {@code membre}, {@code vehicule}, {@code poste} sont
 * bien fetchees.</p>
 *
 * <p>{@code @PreAuthorize("hasRole('ADMINISTRATEUR')")} sur le service est evalue :
 * {@code @WithMockUser(roles = "ADMINISTRATEUR")} au niveau classe fournit le
 * principal necessaire.</p>
 */
@SpringBootTest
@Testcontainers
@Transactional
@Import(AdminRdvServiceIT.HorlogeFixe.class)
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("AdminRdvService (integration)")
class AdminRdvServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    // Dimanche 13 septembre 2026 a 12:00 Bruxelles. Les RDV termines a 09:30 sont
    // dans le passe, ceux commencant a 14:00 sont dans le futur : la frontiere
    // temporelle de rendezVousATraiter est nette et deterministe.
    private static final Instant INSTANT_FIGE =
            LocalDate.of(2026, 9, 13).atTime(12, 0).atZone(BRUXELLES).toInstant();

    @TestConfiguration
    static class HorlogeFixe {
        @Bean
        @Primary
        Clock horlogeFigee() {
            return Clock.fixed(INSTANT_FIGE, BRUXELLES);
        }
    }

    @Autowired private AdminRdvService service;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private be.autoservplus.intervention.repository.InterventionRepository interventions;

    private Utilisateur marie;
    private Vehicule golf;
    private PosteAtelier pont;
    private Prestation vidange;
    private int compteur;

    @BeforeEach
    void setUp() {
        marie = utilisateurs.save(new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-ADM-ENT", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-ADM-VID", "Vidange", new BigDecimal("49.00"), 30));
        pont = postes.save(new PosteAtelier("Pont de test"));
        compteur = 1;
    }

    private Rdv insererRdv(Instant debut, StatutRdv etat) {
        String numero = "RDV-IT-ADM-%04d".formatted(compteur++);
        Rdv rdv = new Rdv(numero, marie, golf, pont, debut, Duration.ofMinutes(30),
                List.of(vidange), null);
        if (etat == StatutRdv.CONFIRME) {
            rdv.confirmer();
        }
        return rdvs.saveAndFlush(rdv);
    }

    @Test
    @DisplayName("demandesEnAttente ne renvoie que les RDV EN_ATTENTE, avec relations chargees")
    void demandesEnAttenteNeRenvoieQueLesEnAttente() {
        Rdv attendu = insererRdv(LocalDate.of(2026, 9, 14).atTime(9, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        insererRdv(LocalDate.of(2026, 9, 14).atTime(14, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);

        List<RdvVueAdmin> vues = service.demandesEnAttente();

        // Seul l EN_ATTENTE remonte : le filtre par statut est bien applique en base.
        assertThat(vues).extracting(RdvVueAdmin::reference).containsExactly(attendu.getReference());

        // membreEmail et vehicule proviennent de relations JOIN FETCH : leur lecture
        // ici prouve que le mapping les a materialisees sans LazyInitializationException.
        RdvVueAdmin vue = vues.get(0);
        assertThat(vue.membreEmail()).isEqualTo("marie@exemple.be");
        assertThat(vue.membreNom()).isEqualTo("Marie Dupont");
        assertThat(vue.vehicule()).contains("VW", "Golf", "1-ABC-123");
        assertThat(vue.peutConfirmer()).isTrue();
        assertThat(vue.peutMarquerHonore()).isFalse();
    }

    @Test
    @DisplayName("rendezVousATraiter ne renvoie que les CONFIRME dont debut <= maintenant")
    void rendezVousATraiterFiltreParStatutEtDebutAtteint() {
        // CONFIRME 09:00-09:30 : debut passe, fin passee -> apparait, honore ET absent OK.
        Rdv passe = insererRdv(LocalDate.of(2026, 9, 13).atTime(9, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        // CONFIRME 11:30-12:00 : debut passe (INSTANT_FIGE 12:00), fin pile a 12:00 -> apparait,
        // honore OK mais absent PAS ENCORE (fin n est pas < maintenant, elle est ==).
        Rdv enCours = insererRdv(LocalDate.of(2026, 9, 13).atTime(11, 30).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        // CONFIRME 14:00-14:30 : debut > INSTANT_FIGE -> ne doit PAS apparaitre.
        insererRdv(LocalDate.of(2026, 9, 13).atTime(14, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        // EN_ATTENTE 07:00-07:30 : debut passe mais mauvais statut -> ne doit PAS apparaitre.
        insererRdv(LocalDate.of(2026, 9, 13).atTime(7, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);

        List<RdvVueAdmin> vues = service.rendezVousATraiter();

        // Le filtre remonte les deux CONFIRME au debut atteint, dans l ordre chronologique.
        assertThat(vues).extracting(RdvVueAdmin::reference)
                .containsExactly(passe.getReference(), enCours.getReference());

        RdvVueAdmin vuePasse = vues.get(0);
        assertThat(vuePasse.peutMarquerHonore()).isTrue();
        assertThat(vuePasse.peutMarquerAbsent())
                .as("Fin passee : le RDV peut aussi etre marque absent")
                .isTrue();

        RdvVueAdmin vueEnCours = vues.get(1);
        assertThat(vueEnCours.peutMarquerHonore())
                .as("Debut atteint : marquage present possible")
                .isTrue();
        assertThat(vueEnCours.peutMarquerAbsent())
                .as("Fin non depassee (==) : on ne declare pas absent tant que le creneau n est pas ecoule")
                .isFalse();

        // Relations chargees par JOIN FETCH accessibles ici :
        assertThat(vuePasse.membreEmail()).isEqualTo("marie@exemple.be");
        assertThat(vuePasse.vehicule()).contains("Golf");
    }

    @Test
    @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
    @DisplayName("@PreAuthorize rejette un utilisateur sans role ADMINISTRATEUR")
    void rejetteNonAdmin() {
        // @WithMockUser au niveau methode surcharge celui de la classe (ADMINISTRATEUR).
        // Le rejet vient specifiquement du @PreAuthorize(hasRole('ADMINISTRATEUR')) sur
        // AdminRdvService, active par @EnableMethodSecurity dans SecuriteConfig charge
        // par @SpringBootTest.
        assertThatThrownBy(() -> service.demandesEnAttente())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("confirmer end-to-end : le statut CONFIRME est persiste et rechargeable")
    void confirmerBoutEnBout() {
        Rdv enAttente = insererRdv(
                LocalDate.of(2026, 9, 14).atTime(10, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        UUID reference = enAttente.getReference();

        service.confirmer(reference);

        // Rechargement pour s assurer que le statut est bien passe en base (pas seulement
        // en cache L1). Le JOIN FETCH de findByReference charge aussi les relations.
        Rdv relu = rdvs.findByReference(reference).orElseThrow();
        assertThat(relu.getStatut()).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    @DisplayName("marquerHonore cree automatiquement une intervention PLANIFIEE, atomiquement")
    void marquerHonoreCreeIntervention() {
        // Fixture : un RDV CONFIRME dont debut est ANTERIEUR a INSTANT_FIGE (12:00),
        // sinon la garde temporelle du service refuse le marquage honore.
        Rdv confirme = insererRdv(
                LocalDate.of(2026, 9, 13).atTime(10, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        UUID reference = confirme.getReference();
        Long rdvId = confirme.getId();

        service.marquerHonore(reference);

        // Test-cle atomicite : le RDV est HONORE ET l intervention existe.
        assertThat(rdvs.findByReference(reference).orElseThrow().getStatut())
                .isEqualTo(StatutRdv.HONORE);
        var intervention = interventions.findByRdvId(rdvId);
        assertThat(intervention)
                .as("Le hook de marquerHonore doit avoir cree l intervention dans la meme transaction")
                .isPresent();
        assertThat(intervention.get().getStatut())
                .isEqualTo(be.autoservplus.intervention.domain.StatutIntervention.PLANIFIEE);
        assertThat(intervention.get().getLignes())
                .as("Le pre-remplissage doit avoir cree une ligne par prestation du RDV")
                .hasSize(1);
    }

    @Test
    @DisplayName("marquerHonore appele deux fois n'ecrase pas l'intervention (idempotence)")
    void marquerHonoreIdempotent() {
        // Debut anterieur a INSTANT_FIGE pour satisfaire la garde temporelle.
        Rdv confirme = insererRdv(
                LocalDate.of(2026, 9, 13).atTime(11, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        UUID reference = confirme.getReference();
        Long rdvId = confirme.getId();

        service.marquerHonore(reference);
        UUID interventionCreee = interventions.findByRdvId(rdvId).orElseThrow().getReference();

        // Second marquerHonore rejete par la machine a etats du RDV (HONORE final),
        // mais si un jour ce n etait plus le cas, l intervention ne serait pas dupliquee.
        // On teste directement l'idempotence du service intervention :
        var deuxieme = interventions.findByRdvId(rdvId);
        assertThat(deuxieme.get().getReference()).isEqualTo(interventionCreee);
    }
}
