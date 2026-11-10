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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    private UUID passeEnAttente;
    private UUID passeConfirme;

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

        // Début passé pour toute horloge réelle : seule la garde du début est en jeu.
        Instant passe = LocalDate.of(2026, 1, 5).atTime(10, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant();
        passeEnAttente = rdvs.saveAndFlush(new Rdv("RDV-TPL-0002", marie, golf, pont, passe,
                Duration.ofMinutes(30), List.of(vidange), null)).getReference();
        Rdv confirme = new Rdv("RDV-TPL-0003", marie, golf, pont, passe.plus(Duration.ofHours(1)),
                Duration.ofMinutes(30), List.of(vidange), null);
        confirme.confirmer(passe.minus(Duration.ofDays(1)));
        passeConfirme = rdvs.saveAndFlush(confirme).getReference();
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
                .andExpect(content().string(not(containsString("RDV-TPL-0001"))));
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
    @DisplayName("demande dont le début est passé : refus proposé, confirmation absente")
    void detailDemandePassee() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}", passeEnAttente).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/rendez-vous/" + passeEnAttente + "/refuser")))
                .andExpect(content().string(not(containsString("/admin/rendez-vous/" + passeEnAttente + "/confirmer"))))
                .andExpect(content().string(not(containsString("/admin/rendez-vous/" + passeEnAttente + "/annuler"))));
    }

    @Test
    @DisplayName("confirmation forcée d'une demande passée : message, statut inchangé")
    void confirmationForceeRefusee() throws Exception {
        mvc.perform(post("/admin/rendez-vous/{ref}/confirmer", passeEnAttente)
                        .param("retour", "detail").with(csrf()).locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/rendez-vous/" + passeEnAttente))
                .andExpect(flash().attribute("erreur", containsString("ne peut plus être confirmé")));
        assertThat(rdvs.findByReference(passeEnAttente).orElseThrow().getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
    }

    @Test
    @DisplayName("rendez-vous confirmé dont le début est passé : honoré proposé, annulation absente")
    void detailConfirmePasse() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}", passeConfirme).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/rendez-vous/" + passeConfirme + "/honorer")))
                .andExpect(content().string(not(containsString("/admin/rendez-vous/" + passeConfirme + "/annuler"))));
        mvc.perform(get("/admin/rendez-vous").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/rendez-vous/" + passeConfirme + "/annuler"))))
                .andExpect(content().string(not(containsString("/admin/rendez-vous/" + passeEnAttente + "/confirmer"))));
    }

    @Test
    @DisplayName("annulation forcée d'un rendez-vous passé : message, statut inchangé")
    void annulationForceeRefusee() throws Exception {
        mvc.perform(post("/admin/rendez-vous/{ref}/annuler", passeConfirme)
                        .param("motif", "Panne du pont").with(csrf()).locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/rendez-vous/" + passeConfirme))
                .andExpect(flash().attribute("erreur", containsString("honoré ou absent")));
        assertThat(rdvs.findByReference(passeConfirme).orElseThrow().getStatut()).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    @DisplayName("GET /admin/rendez-vous/{ref} inconnu : 404")
    void detailInconnu() throws Exception {
        mvc.perform(get("/admin/rendez-vous/{ref}", UUID.randomUUID()).locale(Locale.FRENCH))
                .andExpect(status().isNotFound());
    }
}
