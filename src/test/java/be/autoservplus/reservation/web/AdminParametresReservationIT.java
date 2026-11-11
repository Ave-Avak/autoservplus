package be.autoservplus.reservation.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.SocleIntegration;
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
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Écran « Paramètres de réservation », rendu réel et enregistrement. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Paramètres de réservation au back-office (intégration)")
class AdminParametresReservationIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private ParametreAtelierRepository parametres;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;

    @Test
    @DisplayName("l'écran montre les valeurs en vigueur, confirmation automatique par défaut")
    void affichage() throws Exception {
        mvc.perform(get("/admin/planning/parametres").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paramètres de réservation")))
                .andExpect(content().string(containsString("id=\"confirmationAutomatique\"")))
                .andExpect(content().string(containsString("checked")))
                .andExpect(content().string(containsString("name=\"delaiReponseHeures\" value=\"24\"")))
                .andExpect(content().string(containsString("href=\"/admin/planning/parametres\"")));
    }

    @Test
    @DisplayName("un enregistrement valide met tout à jour et revient à l'écran avec un message")
    void enregistrement() throws Exception {
        mvc.perform(post("/admin/planning/parametres").with(csrf()).locale(Locale.FRENCH)
                        .param("_confirmationAutomatique", "on")
                        .param("delaiReponseHeures", "36")
                        .param("delaiMinimalHeures", "12")
                        .param("delaiAnnulationHeures", "6")
                        .param("delaiRappelHeures", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/planning/parametres"))
                .andExpect(flash().attribute("message", "Les paramètres de réservation sont enregistrés."));

        ParametreAtelier p = parametres.courants();
        assertThat(p.isConfirmationAutomatique()).isFalse();
        assertThat(p.delaiReponse()).isEqualTo(Duration.ofHours(36));
        assertThat(p.delaiMinimal()).isEqualTo(Duration.ofHours(12));
        assertThat(p.delaiAnnulation()).isEqualTo(Duration.ofHours(6));
        assertThat(p.rappelActif()).isFalse();
    }

    @Test
    @DisplayName("une valeur hors bornes est signalée sous son champ, rien n'est enregistré")
    void horsBornes() throws Exception {
        mvc.perform(post("/admin/planning/parametres").with(csrf()).locale(Locale.FRENCH)
                        .param("confirmationAutomatique", "true")
                        .param("delaiReponseHeures", "1")
                        .param("delaiMinimalHeures", "24")
                        .param("delaiAnnulationHeures", "24")
                        .param("delaiRappelHeures", "24"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Le délai de réponse doit être compris entre 2 et 168 heures.")));

        assertThat(parametres.courants().delaiReponse()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be", roles = "MEMBRE")
    @DisplayName("un membre n'ouvre pas l'écran")
    void membreRefuse() throws Exception {
        mvc.perform(get("/admin/planning/parametres")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("changer de mode ne déplace pas l'échéance d'une demande déjà en attente")
    void demandeEnAttenteGardeSonEcheance() throws Exception {
        Utilisateur marie = utilisateurs.save(new Utilisateur("marie.param@exemple.be", "$2a$12$h",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Vehicule golf = vehicules.save(new Vehicule(marie, "1-PAR-001", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-PAR", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-PAR-VID", "Vidange",
                new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont des paramètres"));
        Instant echeance = Instant.parse("2027-03-01T11:00:00Z");
        Rdv demande = new Rdv("RDV-PAR-0001", marie, golf, pont, Instant.parse("2027-03-03T09:00:00Z"),
                Duration.ofMinutes(30), List.of(vidange), null);
        demande.fixerEcheanceReponse(echeance);
        rdvs.saveAndFlush(demande);

        mvc.perform(post("/admin/planning/parametres").with(csrf())
                        .param("confirmationAutomatique", "true")
                        .param("delaiReponseHeures", "2")
                        .param("delaiMinimalHeures", "24")
                        .param("delaiAnnulationHeures", "24")
                        .param("delaiRappelHeures", "24"))
                .andExpect(status().is3xxRedirection());

        assertThat(rdvs.findByReference(demande.getReference()).orElseThrow().getEcheanceReponse())
                .isEqualTo(echeance);
    }
}
