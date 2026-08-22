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
 * Rendu Thymeleaf des templates admin/interventions.html et
 * admin/intervention-detail.html contre le contexte Spring complet.
 * Une erreur de parsing ou un accesseur manquant echoue au build plutot qu'au
 * premier clic en prod. Meme approche que {@code AdminRdvTemplatesIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Templates admin interventions (integration)")
class AdminInterventionTemplatesIT {

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
        Categorie entretien = categories.save(new Categorie("IT-INT", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-INT-VID", "Vidange", new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont de test intervention"));

        Rdv rdv = new Rdv("RDV-INT-0001", marie, golf, pont,
                LocalDate.of(2026, 12, 1).atTime(10, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant(),
                Duration.ofMinutes(30), List.of(vidange), null);
        Rdv sauve = rdvs.saveAndFlush(rdv);

        Intervention it = new Intervention(numeros.prochain(), sauve);
        reference = interventions.saveAndFlush(it).getReference();
    }

    @Test
    @DisplayName("GET /admin/interventions rend la liste sans erreur de template")
    void listeInterventions() throws Exception {
        mvc.perform(get("/admin/interventions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Interventions en cours")));
    }

    @Test
    @DisplayName("GET /admin/interventions/{ref} rend le detail avec les lignes pre-remplies")
    void detailIntervention() throws Exception {
        mvc.perform(get("/admin/interventions/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("marie@exemple.be")))
                .andExpect(content().string(containsString("Vidange")))
                .andExpect(content().string(containsString("Démarrer")));
    }

    /**
     * RM-14, defense en profondeur : le domaine refuse l ajout hors EN_COURS, l ecran
     * ne doit pas y inviter. On verifie sur le rendu reel, pas sur le flag du DTO —
     * c est le template qui decide ce que le garage voit.
     */
    @Test
    @DisplayName("PLANIFIEE : pas de formulaire d'ajout de ligne, mais l'explication (RM-14)")
    void ajoutDeLigneMasqueEnPlanifiee() throws Exception {
        mvc.perform(get("/admin/interventions/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Ajouter une prestation"))))
                .andExpect(content().string(containsString(
                        "Démarrez l'intervention pour pouvoir y ajouter des prestations")))
                // Le commentaire client reste editable : il ne peut pas gonfler le devis.
                .andExpect(content().string(containsString("Commentaire pour le client")));
    }

    @Test
    @DisplayName("EN_COURS : le formulaire d'ajout de ligne apparait")
    void ajoutDeLigneVisibleEnCours() throws Exception {
        Intervention it = interventions.findByReference(reference).orElseThrow();
        it.demarrer(java.time.Instant.parse("2026-12-01T09:00:00Z"));
        interventions.saveAndFlush(it);

        mvc.perform(get("/admin/interventions/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ajouter une prestation")))
                .andExpect(content().string(not(containsString(
                        "Démarrez l'intervention pour pouvoir y ajouter des prestations"))));
    }
}
