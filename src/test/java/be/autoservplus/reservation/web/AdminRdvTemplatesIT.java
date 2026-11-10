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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import be.autoservplus.support.SocleIntegration;

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
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Templates admin (integration)")
class AdminRdvTemplatesIT extends SocleIntegration {

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
        mvc.perform(get("/admin/rendez-vous").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administration des rendez-vous")))
                .andExpect(content().string(containsString("Demandes en attente")))
                .andExpect(content().string(containsString("Rendez-vous du jour à traiter")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref}/refuser rend le formulaire avec le contexte")
    void formulaireRefuser() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}/refuser", reference).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Refuser un rendez-vous")))
                .andExpect(content().string(containsString("RDV-TPL-0001")))
                .andExpect(content().string(containsString("marie@exemple.be")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref}/annuler rend le formulaire avec le contexte")
    void formulaireAnnuler() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}/annuler", reference).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annuler un rendez-vous")))
                .andExpect(content().string(containsString("RDV-TPL-0001")))
                .andExpect(content().string(containsString("marie@exemple.be")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous rend aussi la section des rendez-vous passés sans issue")
    void listeSectionPasses() throws Exception {
        mvc.perform(get("/admin/rendez-vous").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rendez-vous passés sans issue")))
                .andExpect(content().string(containsString("href=\"/admin/rendez-vous/agenda\"")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/agenda rend la période et le lien vers le détail")
    void agenda() throws Exception {
        mvc.perform(get("/admin/rendez-vous/agenda").param("du", "2026-12-01").param("au", "2026-12-01").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agenda des rendez-vous")))
                .andExpect(content().string(containsString("value=\"2026-12-01\"")))
                .andExpect(content().string(containsString("RDV-TPL-0001")))
                .andExpect(content().string(containsString("href=\"/admin/rendez-vous/" + reference + "\"")))
                .andExpect(content().string(containsString("Pont de test")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/agenda sans paramètre : période du jour, sans erreur")
    void agendaParDefaut() throws Exception {
        mvc.perform(get("/admin/rendez-vous/agenda").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"du\"")))
                .andExpect(content().string(containsString("Agenda des rendez-vous")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/agenda sur une période vide : message")
    void agendaVide() throws Exception {
        mvc.perform(get("/admin/rendez-vous/agenda").param("du", "2026-11-30").param("au", "2026-11-30").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun rendez-vous sur cette période.")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/agenda avec une fin avant le début : message, aucun tableau")
    void agendaPeriodeInversee() throws Exception {
        mvc.perform(get("/admin/rendez-vous/agenda").param("du", "2026-12-02").param("au", "2026-12-01").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La date de fin précède la date de début.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("RDV-TPL-0001"))));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/agenda en néerlandais")
    void agendaNeerlandais() throws Exception {
        mvc.perform(get("/admin/rendez-vous/agenda").locale(Locale.forLanguageTag("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agenda van de afspraken")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref} rend le détail et les actions du statut")
    void detail() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}", reference).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rendez-vous RDV-TPL-0001")))
                .andExpect(content().string(containsString("Pont de test")))
                .andExpect(content().string(containsString("Contrôle annuel")))
                .andExpect(content().string(containsString("/admin/rendez-vous/" + reference + "/confirmer")))
                .andExpect(content().string(containsString("name=\"retour\" value=\"detail\"")))
                .andExpect(content().string(containsString("/admin/rendez-vous/" + reference + "/refuser")));
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref} inconnu : 404")
    void detailInconnu() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}", UUID.randomUUID()).locale(Locale.FRENCH))
                .andExpect(status().isNotFound());
    }
}
