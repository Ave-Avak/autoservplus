package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.HistoriqueStatutInterventionRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.GenerateurNumeroIntervention;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import be.autoservplus.support.SocleIntegration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Templates suivi intervention membre (integration)")
class InterventionTemplatesIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionRepository interventions;
    @Autowired private HistoriqueStatutInterventionRepository historiques;
    @Autowired private GenerateurNumeroIntervention numeros;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entites;

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
        mvc.perform(get("/mes-interventions/{ref}", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travaux prévus")))
                .andExpect(content().string(containsString("Vidange")))
                .andExpect(content().string(containsString("hx-trigger=\"every 10s\"")));
    }

    @Test
    @DisplayName("GET /mes-interventions/{ref}/statut rend le fragment isole (pas de <html>)")
    void fragmentStatutIsole() throws Exception {
        mvc.perform(get("/mes-interventions/{ref}/statut", reference).locale(java.util.Locale.FRENCH))
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

        mvc.perform(get("/mes-interventions/{ref}/statut", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                // Statut affiche mais pas de trigger de polling : Thymeleaf omet
                // les attributs a valeur null.
                .andExpect(content().string(containsString("badge")))
                .andExpect(content().string(not(containsString("hx-trigger"))))
                .andExpect(content().string(not(containsString("hx-get"))));
    }

    // --- entrée directe au garage : ni rendez-vous ni commande ------------------------

    /**
     * Détache l'intervention de son rendez-vous, comme les cinq interventions de la graine
     * de démonstration : le schéma l'admet (V32), aucun écran ne le crée.
     */
    private void detacherDuRendezVous() {
        interventions.flush();
        jdbc.update("UPDATE intervention SET rdv_id = NULL WHERE reference = ?", reference);
        entites.clear();
    }

    @Test
    @DisplayName("entrée directe : le propriétaire du véhicule ouvre le suivi")
    void entreeDirecteSuivi() throws Exception {
        detacherDuRendezVous();

        mvc.perform(get("/mes-interventions/{ref}", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vidange")));
    }

    @Test
    @DisplayName("entrée directe en attente d'accord : le propriétaire atteint l'écran de validation")
    void entreeDirecteValidation() throws Exception {
        provoquerDepassement();
        detacherDuRendezVous();

        mvc.perform(get("/mes-interventions/{ref}/validation", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travaux supplémentaires à valider")))
                // 49,00 et 89,00 hors TVA, soit 166,98 à payer, TVA comprise.
                .andExpect(content().string(containsString("138,00")))
                .andExpect(content().string(containsString("166,98")))
                // Aucune réservation derrière une entrée directe : le libellé n'en parle pas.
                .andExpect(content().string(containsString("Devis initial")))
                .andExpect(content().string(not(containsString("réservation"))));
    }

    @Test
    @WithMockUser(username = "paul@exemple.be")
    @DisplayName("entrée directe : un autre membre reçoit 404, au suivi comme à la validation")
    void entreeDirecteAutreMembre() throws Exception {
        utilisateurs.save(new Utilisateur("paul@exemple.be", "$2a$12$h", "Martin", "Paul",
                TypeUtilisateur.MEMBRE));
        provoquerDepassement();
        detacherDuRendezVous();

        mvc.perform(get("/mes-interventions/{ref}", reference)).andExpect(status().isNotFound());
        mvc.perform(get("/mes-interventions/{ref}/validation", reference)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("entrée directe sur un véhicule retiré du parc : pas d'erreur 500")
    void entreeDirecteVehiculeRetire() throws Exception {
        detacherDuRendezVous();
        jdbc.update("UPDATE vehicule SET deleted_at = now() WHERE plaque = '1-ABC-123'");

        int statut = mvc.perform(get("/mes-interventions/{ref}", reference).locale(java.util.Locale.FRENCH))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(200, 404);
    }

    @Test
    @DisplayName("entrée directe dont le propriétaire est anonymisé : 404, pas d'erreur 500")
    void entreeDirecteProprietaireAnonymise() throws Exception {
        detacherDuRendezVous();
        jdbc.update("UPDATE utilisateur SET anonymise_le = now() WHERE email = 'marie@exemple.be'");

        mvc.perform(get("/mes-interventions/{ref}", reference)).andExpect(status().isNotFound());
    }

    // --- RM-15 : encart de validation et ecran de decision ---------------------------

    /**
     * Amene l intervention au-dela du seuil : devis initial 49,00 € HTVA, seuil a
     * 53,90 €, on ajoute 89,00 €. L entite bascule d elle-meme.
     */
    private void provoquerDepassement() {
        Prestation plaquettes = prestations.save(new Prestation(
                categories.findAll().get(0), "IT-SUIVI-FRE", "Plaquettes avant",
                new BigDecimal("89.00"), 60));
        Intervention it = interventions.findByReference(reference).orElseThrow();
        it.demarrer(java.time.Instant.parse("2026-12-01T09:00:00Z"));
        it.ajouterLigneMainOeuvre(plaquettes, BigDecimal.valueOf(1),
                plaquettes.getPrixHtva(), plaquettes.getTauxTva());
        interventions.saveAndFlush(it);
    }

    @Test
    @DisplayName("depassement : l'encart « Validation requise » apparait dans le fragment poll")
    void encartValidationDansLeFragment() throws Exception {
        provoquerDepassement();

        mvc.perform(get("/mes-interventions/{ref}/statut", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Validation requise")))
                .andExpect(content().string(containsString("/validation")))
                // RM-16 : le badge continue d'afficher le percu, pas le statut technique.
                .andExpect(content().string(containsString(">En cours<")))
                .andExpect(content().string(not(containsString("ATTENTE_VALIDATION_MEMBRE"))));
    }

    @Test
    @DisplayName("GET /validation rend l'ecran de decision avec les prix des lignes proposees")
    void ecranValidation() throws Exception {
        provoquerDepassement();

        mvc.perform(get("/mes-interventions/{ref}/validation", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travaux supplémentaires à valider")))
                .andExpect(content().string(containsString("Plaquettes avant")))
                // C'est la seule vue membre qui expose un prix a la ligne : sans lui,
                // l'accord ne serait pas eclaire.
                .andExpect(content().string(containsString("89,00")))
                // Prix annoncés TVA comprise, hors TVA en regard : ligne 107,69, devis initial
                // 59,29, montant à payer 166,98.
                .andExpect(content().string(containsString("Montant TVA comprise")))
                .andExpect(content().string(containsString("107,69")))
                .andExpect(content().string(containsString("59,29")))
                .andExpect(content().string(containsString("166,98")))
                .andExpect(content().string(not(containsString("Montants hors TVA"))))
                .andExpect(content().string(containsString("J&#39;accepte ces travaux")))
                .andExpect(content().string(containsString("Je refuse")));
    }

    @Test
    @DisplayName("GET /validation hors depassement : 404, pas d'ecran de decision vide")
    void ecranValidationHorsDepassement() throws Exception {
        mvc.perform(get("/mes-interventions/{ref}/validation", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la ligne en attente ne figure pas dans « Travaux prévus » ni dans le total")
    void ligneEnAttenteHorsSuivi() throws Exception {
        provoquerDepassement();

        mvc.perform(get("/mes-interventions/{ref}", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vidange")))
                // Tant que le membre n'a pas repondu, la ligne n'est ni due ni prevue :
                // elle vit sur l'ecran de validation, pas dans le recapitulatif.
                .andExpect(content().string(not(containsString("<td>Plaquettes avant</td>"))))
                .andExpect(content().string(containsString("59,29")));
    }

    // --- F17 : chronologie horodatee des changements de statut ------------------------

    @Test
    @DisplayName("F17 : la chronologie s'affiche en percu, les transitions internes fusionnees (RM-16)")
    void chronologieVisibleEnPercu() throws Exception {
        Intervention it = interventions.findByReference(reference).orElseThrow();
        java.time.Instant t0 = java.time.Instant.parse("2026-12-01T08:00:00Z");
        historiques.save(new HistoriqueStatutIntervention(it, null,
                StatutIntervention.PLANIFIEE, t0, null, null));
        historiques.save(new HistoriqueStatutIntervention(it, StatutIntervention.PLANIFIEE,
                StatutIntervention.EN_COURS, t0.plusSeconds(3600), null, null));
        // Suspension : percue « En cours » comme l entree precedente, elle ne doit
        // produire aucune etape supplementaire dans la chronologie du membre.
        historiques.save(new HistoriqueStatutIntervention(it, StatutIntervention.EN_COURS,
                StatutIntervention.SUSPENDUE, t0.plusSeconds(7200), null, null));

        mvc.perform(get("/mes-interventions/{ref}", reference).locale(java.util.Locale.FRENCH))
                .andExpect(status().isOk())
                // Titre et libelles resolus par le MessageSource (cles i18n, locale fr).
                .andExpect(content().string(containsString("Chronologie")))
                // 08:00Z le 1er decembre = 09:00 a Bruxelles. Date pre-formatee cote DTO.
                .andExpect(content().string(containsString("Mardi 1 décembre 2026 09:00")))
                .andExpect(content().string(containsString("En attente")))
                .andExpect(content().string(containsString("En cours")))
                // RM-16 : aucun statut technique ne fuit dans la chronologie.
                .andExpect(content().string(not(containsString("SUSPENDUE"))));
    }

    @Test
    @DisplayName("intervention SUSPENDUE : le membre voit « En cours » (projection RM-16), pas « Suspendue »")
    void statutPercuMasqueLaSuspension() throws Exception {
        // Amener l intervention en SUSPENDUE : PLANIFIEE -> EN_COURS -> SUSPENDUE.
        Intervention it = interventions.findByReference(reference).orElseThrow();
        it.demarrer(java.time.Instant.parse("2026-12-01T09:00:00Z"));
        it.suspendre();
        interventions.saveAndFlush(it);

        mvc.perform(get("/mes-interventions/{ref}/statut", reference).locale(java.util.Locale.FRENCH))
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
