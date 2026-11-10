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
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.rgpd.service.ExportDonneesService;
import be.autoservplus.rgpd.service.SuppressionCompteService;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historique d'entretien par véhicule (F21) et note interne de l'atelier (A16), de bout
 * en bout : propriété du véhicule, absence de la note dans tout rendu membre, présence
 * dans l'export F22, effacement à la suppression de compte F23.
 *
 * <p>La note porte un marqueur introuvable ailleurs, pour qu'une recherche dans le HTML
 * ne puisse pas tomber juste par hasard.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Historique d'entretien et note interne (intégration)")
class HistoriqueEntretienIT extends SocleIntegration {

    private static final String MARIE = "marie.historique@exemple.be";
    private static final String PAUL = "paul.historique@exemple.be";
    private static final String MOT_DE_PASSE = "MonMotDePasse2026!";
    private static final String NOTE = "NOTE-ATELIER-7f3c bruit de roulement";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionRepository interventions;
    @Autowired private GenerateurNumeroIntervention numeros;
    @Autowired private ExportDonneesService export;
    @Autowired private SuppressionCompteService suppression;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private JdbcTemplate jdbc;

    private UUID golf;
    private UUID clio;
    private UUID intervention;
    private String numero;

    @BeforeEach
    void preparer() {
        Utilisateur marie = new Utilisateur(MARIE, encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        marie.confirmerAdresseEmail();
        marie = utilisateurs.save(marie);
        Utilisateur paul = utilisateurs.save(new Utilisateur(PAUL, "$2a$12$h",
                "Martin", "Paul", TypeUtilisateur.MEMBRE));

        Vehicule voitureMarie = vehicules.save(new Vehicule(marie, "1-HIS-001", "VW", "Golf", Motorisation.DIESEL));
        golf = voitureMarie.getReference();
        clio = vehicules.save(new Vehicule(paul, "1-HIS-002", "Renault", "Clio", Motorisation.ESSENCE)).getReference();

        Categorie entretien = categories.save(new Categorie("IT-HIS", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-HIS-VID", "Vidange historique",
                new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont historique"));
        Rdv rdv = rdvs.saveAndFlush(new Rdv("RDV-HIS-0001", marie, voitureMarie, pont,
                LocalDate.of(2026, 12, 1).atTime(10, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant(),
                Duration.ofMinutes(30), List.of(vidange), null));

        Intervention it = new Intervention(numeros.prochain(), rdv);
        it.modifierNoteInterne(NOTE);
        it = interventions.saveAndFlush(it);
        intervention = it.getReference();
        numero = it.getNumero();
    }

    private static RequestPostProcessor marie() {
        return user(MARIE).roles("MEMBRE");
    }

    private static RequestPostProcessor admin() {
        return user("admin.historique@exemple.be").roles("ADMINISTRATEUR");
    }

    @Nested
    @DisplayName("Historique côté membre")
    class Membre {

        @Test
        @DisplayName("le propriétaire voit ses interventions, statut perçu, sans la note interne")
        void proprietaire() throws Exception {
            mvc.perform(get("/mes-vehicules/{ref}/historique", golf).with(marie()).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(numero)))
                    .andExpect(content().string(containsString("Vidange historique")))
                    .andExpect(content().string(containsString("01/12/2026")))
                    .andExpect(content().string(containsString("En attente")))
                    .andExpect(content().string(not(containsString("PLANIFIEE"))))
                    .andExpect(content().string(not(containsString("NOTE-ATELIER-7f3c"))));
        }

        @Test
        @DisplayName("véhicule d'autrui ou inconnu : 404, jamais 403")
        void autrui() throws Exception {
            mvc.perform(get("/mes-vehicules/{ref}/historique", clio).with(marie()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/mes-vehicules/{ref}/historique", golf).with(user(PAUL).roles("MEMBRE")))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/mes-vehicules/{ref}/historique", UUID.randomUUID()).with(marie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("un véhicule sans intervention affiche une liste vide")
        void listeVide() throws Exception {
            mvc.perform(get("/mes-vehicules/{ref}/historique", clio).with(user(PAUL).roles("MEMBRE"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Aucune intervention")));
        }

        @Test
        @DisplayName("le lien part de la page des véhicules")
        void lienDepuisLesVehicules() throws Exception {
            mvc.perform(get("/mes-vehicules").with(marie()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/mes-vehicules/" + golf + "/historique")));
        }

        @Test
        @DisplayName("l'écran de suivi de l'intervention ne montre pas la note")
        void suiviSansNote() throws Exception {
            mvc.perform(get("/mes-interventions/{ref}", intervention).with(marie()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("NOTE-ATELIER-7f3c"))));
        }
    }

    @Nested
    @DisplayName("Back-office")
    class BackOffice {

        @Test
        @DisplayName("l'historique complet montre la note, et le détail y mène")
        void historiqueComplet() throws Exception {
            mvc.perform(get("/admin/interventions/{ref}", intervention).with(admin()).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/admin/vehicules/" + golf + "/historique")))
                    .andExpect(content().string(containsString("droit d")));
            mvc.perform(get("/admin/vehicules/{ref}/historique", golf).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(numero)))
                    .andExpect(content().string(containsString(NOTE)));
        }

        @Test
        @DisplayName("un véhicule retiré du parc garde son historique au back-office")
        void vehiculeRetire() throws Exception {
            jdbc.update("UPDATE vehicule SET deleted_at = now() WHERE reference = ?", golf);
            mvc.perform(get("/admin/vehicules/{ref}/historique", golf).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(numero)));
            mvc.perform(get("/mes-vehicules/{ref}/historique", golf).with(marie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("l'administrateur modifie la note, un membre est refusé")
        void modificationReserveeAuGarage() throws Exception {
            mvc.perform(post("/admin/interventions/{ref}/note-interne", intervention)
                            .with(admin()).with(csrf()).param("noteInterne", "Note revue"))
                    .andExpect(redirectedUrl("/admin/interventions/" + intervention));
            assertThat(note()).isEqualTo("Note revue");

            mvc.perform(post("/admin/interventions/{ref}/note-interne", intervention)
                            .with(marie()).with(csrf()).param("noteInterne", "Tentative du membre"))
                    .andExpect(status().isForbidden());
            assertThat(note()).isEqualTo("Note revue");
        }

        @Test
        @DisplayName("une note trop longue est refusée avec un message, la note en place reste")
        void noteTropLongue() throws Exception {
            mvc.perform(post("/admin/interventions/{ref}/note-interne", intervention)
                            .with(admin()).with(csrf())
                            .param("noteInterne", "x".repeat(Intervention.LONGUEUR_NOTE_INTERNE + 1)))
                    .andExpect(redirectedUrl("/admin/interventions/" + intervention));
            assertThat(note()).isEqualTo(NOTE);
        }
    }

    @Nested
    @DisplayName("RGPD")
    class Rgpd {

        @Test
        @WithMockUser(username = MARIE, roles = "MEMBRE")
        @DisplayName("l'export F22 restitue la note interne")
        void export() {
            ExportDonnees donnees = export.assembler(MARIE);
            assertThat(donnees.donneesPersonnelles().interventions())
                    .singleElement()
                    .satisfies(i -> assertThat(i.noteInterneAtelier()).isEqualTo(NOTE));
        }

        @Test
        @WithMockUser(username = MARIE, roles = "MEMBRE")
        @DisplayName("la suppression de compte F23 vide la note, l'intervention reste")
        void anonymisation() {
            suppression.supprimer(MARIE, MOT_DE_PASSE, "SUPPRIMER");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM intervention WHERE reference = ?", Integer.class, intervention))
                    .isEqualTo(1);
            assertThat(note()).isNull();
        }
    }

    private String note() {
        return jdbc.queryForObject("SELECT note_interne FROM intervention WHERE reference = ?",
                String.class, intervention);
    }
}
