package be.autoservplus.reservation.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu Thymeleaf des trois templates admin contre le contexte Spring Boot complet.
 *
 * <p>Complementaire de {@code AdminRdvControllerTest} qui vit dans un slice
 * @WebMvcTest sans templates. Ici on charge le vrai moteur Thymeleaf : une erreur
 * de parsing, un accesseur de record oublie ou un attribut de modele mal nomme
 * font echouer le test au premier rendu, plutot qu au premier clic en prod.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Templates admin (integration)")
class AdminRdvTemplatesIT {

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

    private UUID reference;

    @BeforeEach
    void setUp() {
        Utilisateur marie = utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Vehicule golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-TPL", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-TPL-VID", "Vidange", new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont de test"));

        Rdv rdv = new Rdv("RDV-TPL-0001", marie, golf, pont,
                LocalDate.of(2026, 12, 1).atTime(10, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant(),
                Duration.ofMinutes(30), List.of(vidange), "Contrôle annuel");
        reference = rdvs.saveAndFlush(rdv).getReference();
    }

    @Test
    @DisplayName("GET /admin/rendez-vous rend la liste sans erreur de template")
    void listeRendezVous() throws Exception {
        mvc.perform(get("/admin/rendez-vous"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administration des rendez-vous")))
                .andExpect(content().string(containsString("Demandes en attente")))
                .andExpect(content().string(containsString("À clôturer")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref}/refuser rend le formulaire avec le contexte")
    void formulaireRefuser() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}/refuser", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Refuser un rendez-vous")))
                .andExpect(content().string(containsString("RDV-TPL-0001")))
                .andExpect(content().string(containsString("marie@exemple.be")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref}/annuler rend le formulaire avec le contexte")
    void formulaireAnnuler() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}/annuler", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annuler un rendez-vous")))
                .andExpect(content().string(containsString("RDV-TPL-0001")))
                .andExpect(content().string(containsString("marie@exemple.be")));
    }
}
