package be.autoservplus.reservation.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.DisponibiliteService;
import be.autoservplus.reservation.service.dto.ConflitRdv;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Écrans du planning (A24) contre le contexte complet : écriture des plages, des postes
 * et des indisponibilités, traduction de {@code ex_plage_ouverture_chevauchement},
 * liste des conflits, disponibilités recalculées et refus d'accès.
 *
 * <p>Classe non transactionnelle : la violation d'une contrainte avorte la transaction
 * PostgreSQL en cours, et une transaction de test partagée avec la requête MockMvc ne
 * pourrait plus rien lire ensuite. En exécution réelle, chaque requête a la sienne.
 * L'état est remis à zéro avant chaque cas, sur la base dédiée à la classe.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminPlanningIT.HorlogeFixe.class)
@DisplayName("Planning du back-office (integration)")
class AdminPlanningIT extends SocleIntegration {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    /** Mardi 10 novembre 2026 à 09:00. Le délai minimal de 24 h et l'horizon de 60 jours encadrent les dates ci-dessous. */
    private static final Instant MAINTENANT = LocalDate.of(2026, 11, 10).atTime(9, 0).atZone(BRUXELLES).toInstant();
    private static final LocalDate JEUDI = LocalDate.of(2026, 11, 19);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 11, 20);

    @TestConfiguration
    static class HorlogeFixe {
        @Bean
        @Primary
        Clock horlogePlanning() {
            return Clock.fixed(MAINTENANT, BRUXELLES);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlageOuvertureRepository plages;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private IndisponibiliteRepository indisponibilites;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private DisponibiliteService disponibilites;

    private Utilisateur marie;
    private Vehicule golf;
    private Prestation vidange;
    private PosteAtelier pont1;
    private PosteAtelier pont2;

    @BeforeEach
    void etatInitial() {
        jdbc.update("DELETE FROM rdv");
        jdbc.update("DELETE FROM indisponibilite");
        jdbc.update("DELETE FROM plage_ouverture");
        jdbc.update("DELETE FROM poste_atelier");

        marie = utilisateurs.findByEmailIgnoreCase("marie.planning@exemple.be").orElseGet(() -> {
            Utilisateur u = new Utilisateur("marie.planning@exemple.be", "{noop}x", "Dupont", "Marie",
                    TypeUtilisateur.MEMBRE);
            u.confirmerAdresseEmail();
            u.setStatut(StatutUtilisateur.ACTIF);
            return utilisateurs.save(u);
        });
        golf = vehicules.findByMembre(marie.getEmail()).stream().findFirst()
                .orElseGet(() -> vehicules.save(new Vehicule(marie, "1-PLA-001", "VW", "Golf", Motorisation.DIESEL)));
        vidange = prestations.findByCode("IT-PLA-VID").orElseGet(() -> {
            Categorie entretien = categories.save(new Categorie("IT-PLA-ENT", "Entretien", TypeCategorie.SERVICE));
            return prestations.save(new Prestation(entretien, "IT-PLA-VID", "Vidange", new BigDecimal("49.00"), 60));
        });
        pont1 = postes.save(new PosteAtelier("Pont 1"));
        pont2 = postes.save(new PosteAtelier("Pont 2"));
        plages.save(new PlageOuverture(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        plages.save(new PlageOuverture(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(18, 0)));
    }

    // --- plages ---------------------------------------------------------------------------

    @Nested
    @DisplayName("plages d'ouverture")
    class Plages {

        @Test
        @DisplayName("une plage créée par l'écran garde ses heures locales, en base comme au calcul des créneaux")
        void creationSansDecalageDeFuseau() throws Exception {
            mvc.perform(admin(post("/admin/planning/plages"))
                            .param("jour", "7").param("heureDebut", "08:00").param("heureFin", "12:00")
                            .param("actif", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/planning/plages"));

            // Lecture brute, hors Hibernate : c'est ce qui avait masqué le décalage d'une heure.
            assertThat(jdbc.queryForObject(
                    "SELECT heure_debut::text || '-' || heure_fin::text FROM plage_ouverture WHERE jour_semaine = 7",
                    String.class)).isEqualTo("08:00:00-12:00:00");
            PlageOuverture lue = plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 7).get(0);
            assertThat(lue.getHeureDebut()).isEqualTo(LocalTime.of(8, 0));

            LocalDate dimanche = LocalDate.of(2026, 11, 22);
            List<CreneauDisponible> creneaux = disponibilites.creneauxDuJour(dimanche, 60);
            assertThat(creneaux).isNotEmpty();
            assertThat(creneaux.get(0).debut()).isEqualTo(dimanche.atTime(8, 0).atZone(BRUXELLES).toInstant());
            assertThat(creneaux.get(creneaux.size() - 1).fin())
                    .isEqualTo(dimanche.atTime(12, 0).atZone(BRUXELLES).toInstant());

            mvc.perform(admin(get("/admin/planning/plages")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Dimanche")))
                    .andExpect(content().string(containsString("08:00")));
        }

        @Test
        @DisplayName("le chevauchement est refusé sous le champ, sans page d'erreur, dans la langue de la session")
        void chevauchementTraduit() throws Exception {
            mvc.perform(admin(post("/admin/planning/plages"))
                            .param("jour", "4").param("heureDebut", "17:00").param("heureFin", "19:00")
                            .param("actif", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Cette plage chevauche une autre plage du même jour.")));

            mvc.perform(admin(post("/admin/planning/plages")).locale(Locale.forLanguageTag("nl"))
                            .param("jour", "4").param("heureDebut", "07:00").param("heureFin", "09:00")
                            .param("actif", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Dit tijdvak overlapt een ander tijdvak van dezelfde dag.")));

            assertThat(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 4)).hasSize(1);
        }

        @Test
        @DisplayName("une modification qui chevauche est refusée de la même façon")
        void chevauchementEnModification() throws Exception {
            PlageOuverture apresMidi = plages.save(new PlageOuverture(DayOfWeek.SATURDAY, LocalTime.of(13, 0), LocalTime.of(17, 0)));
            plages.save(new PlageOuverture(DayOfWeek.SATURDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

            mvc.perform(admin(post("/admin/planning/plages/" + apresMidi.getId() + "/modifier"))
                            .param("jour", "6").param("heureDebut", "11:00").param("heureFin", "17:00")
                            .param("actif", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Cette plage chevauche une autre plage du même jour.")));

            assertThat(plages.parIdentifiant(apresMidi.getId()).orElseThrow().getHeureDebut()).isEqualTo(LocalTime.of(13, 0));
        }

        @Test
        @DisplayName("une fin avant le début est refusée sous le champ de fin")
        void finAvantDebut() throws Exception {
            mvc.perform(admin(post("/admin/planning/plages"))
                            .param("jour", "7").param("heureDebut", "12:00").param("heureFin", "08:00")
                            .param("actif", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("L&#39;heure de fin doit suivre l&#39;heure de début.")));
            assertThat(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 7)).isEmpty();
        }

        @Test
        @DisplayName("réduire une plage liste exactement les rendez-vous découverts, sans annulé ni hors période")
        void reductionListeLesConflits() throws Exception {
            Rdv aDecouvrir = rdvs.saveAndFlush(confirme(rdv("RDV-PLA-0001", pont1, JEUDI, 14)));
            rdvs.saveAndFlush(annule(rdv("RDV-PLA-0002", pont2, JEUDI, 14)));
            rdvs.saveAndFlush(rdv("RDV-PLA-0003", pont1, JEUDI, 9));
            rdvs.saveAndFlush(rdv("RDV-PLA-0004", pont1, VENDREDI, 14));
            Long jeudi = plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 4).get(0).getId();

            MvcResult resultat = mvc.perform(admin(post("/admin/planning/plages/" + jeudi + "/modifier"))
                            .param("jour", "4").param("heureDebut", "08:00").param("heureFin", "12:00")
                            .param("actif", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(conflits(resultat)).extracting(ConflitRdv::numero).containsExactly("RDV-PLA-0001");
            assertThat(rdvs.findByReference(aDecouvrir.getReference()).orElseThrow().getStatut())
                    .as("le rendez-vous reste intact").isEqualTo(aDecouvrir.getStatut());

            mvc.perform(admin(get("/admin/planning/plages")).flashAttr("conflits", conflits(resultat)))
                    .andExpect(content().string(containsString("RDV-PLA-0001")))
                    .andExpect(content().string(containsString("Marie Dupont")))
                    .andExpect(content().string(containsString("Vidange")))
                    .andExpect(content().string(containsString(
                            "/admin/rendez-vous/" + aDecouvrir.getReference() + "/annuler")));
        }

        @Test
        @DisplayName("les disponibilités suivent la modification de la plage")
        void disponibilitesRecalculees() throws Exception {
            assertThat(disponibilites.creneauxDuJour(JEUDI, 60))
                    .anyMatch(c -> c.debut().equals(JEUDI.atTime(15, 0).atZone(BRUXELLES).toInstant()));
            Long jeudi = plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 4).get(0).getId();

            mvc.perform(admin(post("/admin/planning/plages/" + jeudi + "/modifier"))
                            .param("jour", "4").param("heureDebut", "08:00").param("heureFin", "12:00")
                            .param("actif", "true"))
                    .andExpect(status().is3xxRedirection());

            List<CreneauDisponible> apres = disponibilites.creneauxDuJour(JEUDI, 60);
            assertThat(apres).isNotEmpty();
            assertThat(apres).allMatch(c -> !c.fin().isAfter(JEUDI.atTime(12, 0).atZone(BRUXELLES).toInstant()));
        }

        @Test
        @DisplayName("retirer une plage la supprime logiquement et liste les rendez-vous laissés sans ouverture")
        void retrait() throws Exception {
            rdvs.saveAndFlush(rdv("RDV-PLA-0005", pont1, VENDREDI, 10));
            Long vendredi = plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 5).get(0).getId();

            MvcResult resultat = mvc.perform(admin(post("/admin/planning/plages/" + vendredi + "/retirer")))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(conflits(resultat)).extracting(ConflitRdv::numero).containsExactly("RDV-PLA-0005");
            assertThat(jdbc.queryForObject("SELECT deleted_by FROM plage_ouverture WHERE id = ?", String.class, vendredi))
                    .isEqualTo("admin.planning@exemple.be");
            assertThat(disponibilites.creneauxDuJour(VENDREDI, 60)).isEmpty();
            mvc.perform(admin(get("/admin/planning/plages/" + vendredi + "/modifier"))).andExpect(status().isNotFound());
        }
    }

    // --- postes ---------------------------------------------------------------------------

    @Nested
    @DisplayName("postes d'atelier")
    class Postes {

        @Test
        @DisplayName("création et modification, libellé unique refusé sous le champ")
        void creationEtModification() throws Exception {
            mvc.perform(admin(post("/admin/planning/postes"))
                            .param("libelle", "Baie diagnostic").param("ordre", "3"))
                    .andExpect(status().is3xxRedirection());
            PosteAtelier baie = postes.findAllByOrderByOrdreAscLibelleAsc().stream()
                    .filter(p -> p.getLibelle().equals("Baie diagnostic")).findFirst().orElseThrow();
            assertThat(baie.getOrdre()).isEqualTo((short) 3);

            mvc.perform(admin(post("/admin/planning/postes/" + baie.getReference() + "/modifier"))
                            .param("libelle", "pont 1").param("ordre", "3"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Un autre poste porte déjà ce libellé.")));

            mvc.perform(admin(post("/admin/planning/postes/" + baie.getReference() + "/modifier"))
                            .param("libelle", "Baie de diagnostic").param("description", "Valise OBD")
                            .param("ordre", "4"))
                    .andExpect(status().is3xxRedirection());
            PosteAtelier relu = postes.findByReference(baie.getReference()).orElseThrow();
            assertThat(relu.getLibelle()).isEqualTo("Baie de diagnostic");
            assertThat(relu.getDescription()).isEqualTo("Valise OBD");
        }

        @Test
        @DisplayName("RM-29 : un poste sans rendez-vous est supprimé physiquement")
        void suppressionPhysique() throws Exception {
            indisponibilites.save(new Indisponibilite(pont2, instant(JEUDI, 8), instant(JEUDI, 12), "Révision du pont"));

            mvc.perform(admin(get("/admin/planning/postes/" + pont2.getReference() + "/supprimer")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Oui, supprimer définitivement")));
            mvc.perform(admin(post("/admin/planning/postes/" + pont2.getReference() + "/supprimer")))
                    .andExpect(status().is3xxRedirection());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM poste_atelier WHERE id = ?", Long.class, pont2.getId()))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM indisponibilite", Long.class)).isZero();
        }

        @Test
        @DisplayName("RM-29 : un poste référencé n'est que désactivé, et ses rendez-vous à venir sont listés")
        void desactivationSiReference() throws Exception {
            rdvs.saveAndFlush(confirme(rdv("RDV-PLA-0010", pont1, JEUDI, 10)));
            rdvs.saveAndFlush(annule(rdv("RDV-PLA-0011", pont1, VENDREDI, 10)));
            rdvs.saveAndFlush(rdv("RDV-PLA-0012", pont2, JEUDI, 10));

            mvc.perform(admin(get("/admin/planning/postes/" + pont1.getReference() + "/supprimer")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("seule la désactivation est proposée")));
            mvc.perform(admin(post("/admin/planning/postes/" + pont1.getReference() + "/supprimer")))
                    .andExpect(status().is3xxRedirection());
            assertThat(postes.findByReference(pont1.getReference())).as("refusé au POST forgé").isPresent();

            MvcResult resultat = mvc.perform(admin(post("/admin/planning/postes/" + pont1.getReference() + "/desactiver")))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(conflits(resultat)).extracting(ConflitRdv::numero).containsExactly("RDV-PLA-0010");
            assertThat(postes.findByReference(pont1.getReference()).orElseThrow().isActif()).isFalse();
            assertThat(disponibilites.creneauxDuJour(JEUDI, 60)).isNotEmpty()
                    .allMatch(c -> c.poste().getId().equals(pont2.getId()) && c.postesLibres() == 1);
        }
    }

    // --- indisponibilités ---------------------------------------------------------------

    @Nested
    @DisplayName("indisponibilités")
    class Indisponibilites {

        @Test
        @DisplayName("une indisponibilité d'un poste liste ses seuls rendez-vous actifs de la période")
        void conflitsExacts() throws Exception {
            rdvs.saveAndFlush(confirme(rdv("RDV-PLA-0020", pont1, JEUDI, 10)));
            rdvs.saveAndFlush(annule(rdv("RDV-PLA-0021", pont1, JEUDI, 13)));
            rdvs.saveAndFlush(rdv("RDV-PLA-0022", pont2, JEUDI, 10));
            rdvs.saveAndFlush(rdv("RDV-PLA-0023", pont1, JEUDI, 16));
            rdvs.saveAndFlush(rdv("RDV-PLA-0024", pont1, VENDREDI, 10));

            MvcResult resultat = mvc.perform(admin(post("/admin/planning/indisponibilites"))
                            .param("posteReference", pont1.getReference().toString())
                            .param("debut", "2026-11-19T08:00").param("fin", "2026-11-19T15:00")
                            .param("motif", "Pont en panne"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(conflits(resultat)).extracting(ConflitRdv::numero).containsExactly("RDV-PLA-0020");
            Indisponibilite enregistree = indisponibilites.aVenir(MAINTENANT).get(0);
            assertThat(enregistree.getDebut()).as("saisie en heure locale du garage")
                    .isEqualTo(instant(JEUDI, 8));
            assertThat(disponibilites.creneauxDuJour(JEUDI, 60))
                    .filteredOn(c -> c.debut().equals(instant(JEUDI, 12)))
                    .singleElement()
                    .matches(c -> c.postesLibres() == 1 && c.poste().getId().equals(pont2.getId()));
        }

        @Test
        @DisplayName("une fermeture de tout l'atelier touche tous les postes, et sa modification recalcule la liste")
        void fermetureAtelierEtModification() throws Exception {
            rdvs.saveAndFlush(rdv("RDV-PLA-0030", pont1, JEUDI, 10));
            rdvs.saveAndFlush(rdv("RDV-PLA-0031", pont2, JEUDI, 14));

            MvcResult creation = mvc.perform(admin(post("/admin/planning/indisponibilites"))
                            .param("posteReference", "")
                            .param("debut", "2026-11-19T00:00").param("fin", "2026-11-20T00:00")
                            .param("motif", "Formation"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat(conflits(creation)).extracting(ConflitRdv::numero)
                    .containsExactly("RDV-PLA-0030", "RDV-PLA-0031");
            assertThat(disponibilites.creneauxDuJour(JEUDI, 60)).isEmpty();

            Indisponibilite formation = indisponibilites.aVenir(MAINTENANT).get(0);
            MvcResult modification = mvc.perform(admin(post(
                            "/admin/planning/indisponibilites/" + formation.getReference() + "/modifier"))
                            .param("posteReference", "")
                            .param("debut", "2026-11-19T12:00").param("fin", "2026-11-19T18:00")
                            .param("motif", "Formation l'après-midi"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat(conflits(modification)).extracting(ConflitRdv::numero).containsExactly("RDV-PLA-0031");

            mvc.perform(admin(post("/admin/planning/indisponibilites/" + formation.getReference() + "/retirer")))
                    .andExpect(status().is3xxRedirection());
            assertThat(indisponibilites.aVenir(MAINTENANT)).isEmpty();
        }

        @Test
        @DisplayName("une fin avant le début est refusée sous le champ de fin")
        void finAvantDebut() throws Exception {
            mvc.perform(admin(post("/admin/planning/indisponibilites"))
                            .param("debut", "2026-11-19T12:00").param("fin", "2026-11-19T08:00")
                            .param("motif", "Erreur"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("La fin doit suivre le début.")));
            assertThat(indisponibilites.aVenir(MAINTENANT)).isEmpty();
        }

        @Test
        @DisplayName("motif vide et dates inversées sont signalés au même envoi, motif obligatoire distinct de la longueur")
        void erreursDuMemeEnvoi() throws Exception {
            mvc.perform(admin(post("/admin/planning/indisponibilites"))
                            .param("debut", "2026-11-19T12:00").param("fin", "2026-11-19T08:00")
                            .param("motif", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Indiquez un motif.")))
                    .andExpect(content().string(containsString("La fin doit suivre le début.")))
                    .andExpect(content().string(not(containsString("200 caractères"))));
            assertThat(indisponibilites.aVenir(MAINTENANT)).isEmpty();
        }

        @Test
        @DisplayName("« Retirer » passe par une page de confirmation")
        void confirmationAvantRetrait() throws Exception {
            mvc.perform(admin(post("/admin/planning/indisponibilites"))
                            .param("posteReference", "")
                            .param("debut", "2026-11-19T00:00").param("fin", "2026-11-20T00:00")
                            .param("motif", "Inventaire"))
                    .andExpect(status().is3xxRedirection());
            UUID reference = indisponibilites.aVenir(MAINTENANT).get(0).getReference();

            mvc.perform(admin(get("/admin/planning/indisponibilites")))
                    .andExpect(content().string(containsString(
                            "href=\"/admin/planning/indisponibilites/" + reference + "/retirer\"")));
            mvc.perform(admin(get("/admin/planning/indisponibilites/" + reference + "/retirer")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Inventaire")))
                    .andExpect(content().string(containsString(
                            "action=\"/admin/planning/indisponibilites/" + reference + "/retirer\"")));
            assertThat(indisponibilites.aVenir(MAINTENANT)).hasSize(1);
        }
    }

    // --- présentation -------------------------------------------------------------------

    @Nested
    @DisplayName("présentation")
    class Presentation {

        @Test
        @DisplayName("« Retirer » une plage passe par une page de confirmation")
        void confirmationRetraitPlage() throws Exception {
            Long jeudi = plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 4).get(0).getId();
            mvc.perform(admin(get("/admin/planning/plages")))
                    .andExpect(content().string(containsString(
                            "href=\"/admin/planning/plages/" + jeudi + "/retirer\"")));
            mvc.perform(admin(get("/admin/planning/plages/" + jeudi + "/retirer")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Jeudi 08:00-18:00")))
                    .andExpect(content().string(containsString(
                            "action=\"/admin/planning/plages/" + jeudi + "/retirer\"")));
            assertThat(plages.parIdentifiant(jeudi)).isPresent();
        }

        @Test
        @DisplayName("la case « Plage active » et le choix du poste sont reliés à leur libellé")
        void libellesRelies() throws Exception {
            mvc.perform(admin(get("/admin/planning/plages/nouvelle")))
                    .andExpect(content().string(containsString("for=\"actif\"")))
                    .andExpect(content().string(containsString("id=\"actif\"")));
            mvc.perform(admin(get("/admin/planning/indisponibilites/nouvelle")))
                    .andExpect(content().string(containsString("for=\"posteReference\"")))
                    .andExpect(content().string(containsString("id=\"posteReference\"")));
        }

        @Test
        @DisplayName("aucun code de règle n'apparaît dans les écrans du planning")
        void aucunCodeDeRegle() throws Exception {
            rdvs.saveAndFlush(confirme(rdv("RDV-PLA-RM", pont1, JEUDI, 9)));
            for (String page : List.of("/admin/planning/plages", "/admin/planning/plages/nouvelle",
                    "/admin/planning/postes", "/admin/planning/postes/nouveau",
                    "/admin/planning/postes/" + pont1.getReference() + "/supprimer",
                    "/admin/planning/indisponibilites", "/admin/planning/indisponibilites/nouvelle")) {
                mvc.perform(admin(get(page)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(not(containsString("RM-"))));
            }
        }
    }

    // --- accès ----------------------------------------------------------------------------

    @Nested
    @DisplayName("accès")
    class Acces {

        @Test
        @DisplayName("un membre ne lit ni n'écrit le planning")
        void refusAuMembre() throws Exception {
            Long jeudi = plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 4).get(0).getId();
            RequestPostProcessor membre = user(marie.getEmail()).roles("MEMBRE");

            mvc.perform(get("/admin/planning/plages").with(membre)).andExpect(status().isForbidden());
            mvc.perform(get("/admin/planning/postes").with(membre)).andExpect(status().isForbidden());
            mvc.perform(get("/admin/planning/indisponibilites").with(membre)).andExpect(status().isForbidden());
            mvc.perform(post("/admin/planning/plages/" + jeudi + "/retirer").with(membre).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/planning/postes/" + pont1.getReference() + "/desactiver").with(membre).with(csrf()))
                    .andExpect(status().isForbidden());

            assertThat(plages.parIdentifiant(jeudi)).isPresent();
            assertThat(postes.findByReference(pont1.getReference()).orElseThrow().isActif()).isTrue();
        }
    }

    // --- aides ----------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder requete) {
        return requete.with(user("admin.planning@exemple.be").roles("ADMINISTRATEUR")).with(csrf())
                .locale(Locale.FRENCH);
    }

    @SuppressWarnings("unchecked")
    private static List<ConflitRdv> conflits(MvcResult resultat) {
        Object valeur = resultat.getFlashMap().get("conflits");
        assertThat(valeur).as("liste des conflits en attribut flash").isNotNull();
        return (List<ConflitRdv>) valeur;
    }

    private static Instant instant(LocalDate jour, int heure) {
        return jour.atTime(heure, 0).atZone(BRUXELLES).toInstant();
    }

    private Rdv rdv(String numero, PosteAtelier poste, LocalDate jour, int heure) {
        return new Rdv(numero, marie, golf, poste, instant(jour, heure), Duration.ofMinutes(30), List.of(vidange), null);
    }

    private static Rdv confirme(Rdv rdv) {
        rdv.confirmer();
        return rdv;
    }

    private static Rdv annule(Rdv rdv) {
        rdv.annulerParLeGarage("Test", MAINTENANT);
        return rdv;
    }
}
