package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.GenerateurNumeroIntervention;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu du template membre intervention/suivi.html et de son fragment blocStatut
 * contre le contexte Spring complet. Detecte un accesseur manque, un typo dans
 * une expression ou une reference de fragment cassee au build, pas au premier
 * clic membre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Templates suivi intervention membre (integration)")
class InterventionTemplatesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionRepository interventions;
    @Autowired private GenerateurNumeroIntervention numeros;

    private UUID reference;

    @BeforeEach
    void setUp() {
        Utilisateur marie = utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Vehicule golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-SUIVI", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-SUIVI-VID", "Vidange", new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont de test suivi"));

        Rdv rdv = new Rdv("RDV-SUIVI-0001", marie, golf, pont,
                LocalDate.of(2026, 12, 1).atTime(10, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant(),
                Duration.ofMinutes(30), List.of(vidange), null);
        Rdv sauve = rdvs.saveAndFlush(rdv);

        Intervention it = new Intervention(numeros.prochain(), sauve);
        reference = interventions.saveAndFlush(it).getReference();
    }

    @Test
    @DisplayName("GET /mes-interventions/{ref} rend la page complete de suivi")
    void pageComplete() throws Exception {
        mvc.perform(get("/mes-interventions/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travaux prévus")))
                .andExpect(content().string(containsString("Vidange")))
                .andExpect(content().string(containsString("hx-trigger=\"every 10s\"")));
    }

    @Test
    @DisplayName("GET /mes-interventions/{ref}/statut rend le fragment isole (pas de <html>)")
    void fragmentStatutIsole() throws Exception {
        mvc.perform(get("/mes-interventions/{ref}/statut", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("badge")))
                // Le fragment n est pas une page complete : pas de <html> ni de <header>.
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("<header"))));
    }

    @Test
    @DisplayName("intervention terminee : le fragment n'inclut plus hx-trigger (polling arrete)")
    void pollingArreteEnTerminee() throws Exception {
        // Amener l intervention en TERMINEE via l API domaine (l orphaned Rdv est
        // conserve, le seul champ qui change est le statut).
        Intervention it = interventions.findByReference(reference).orElseThrow();
        it.demarrer(java.time.Instant.parse("2026-12-01T09:00:00Z"));
        it.terminer(java.time.Instant.parse("2026-12-01T10:00:00Z"));
        interventions.saveAndFlush(it);

        mvc.perform(get("/mes-interventions/{ref}/statut", reference))
                .andExpect(status().isOk())
                // Statut affiche mais pas de trigger de polling : Thymeleaf omet
                // les attributs a valeur null.
                .andExpect(content().string(containsString("badge")))
                .andExpect(content().string(not(containsString("hx-trigger"))))
                .andExpect(content().string(not(containsString("hx-get"))));
    }

    @Test
    @DisplayName("intervention SUSPENDUE : le membre voit « En cours » (projection RM-16), pas « Suspendue »")
    void statutPercuMasqueLaSuspension() throws Exception {
        // Amener l intervention en SUSPENDUE : PLANIFIEE -> EN_COURS -> SUSPENDUE.
        Intervention it = interventions.findByReference(reference).orElseThrow();
        it.demarrer(java.time.Instant.parse("2026-12-01T09:00:00Z"));
        it.suspendre();
        interventions.saveAndFlush(it);

        mvc.perform(get("/mes-interventions/{ref}/statut", reference))
                .andExpect(status().isOk())
                // Le texte affiche est le percu, pas le libelle technique.
                .andExpect(content().string(containsString(">En cours<")))
                // Le libelle technique « Suspendue » (majuscule initiale, tel
                // qu affiche cote admin) ne doit pas apparaitre dans le rendu
                // membre. La classe CSS badge-suspendue reste presente en
                // minuscule (invisible, utile au styling) : c est acceptable.
                .andExpect(content().string(not(containsString("Suspendue"))));
    }
}
