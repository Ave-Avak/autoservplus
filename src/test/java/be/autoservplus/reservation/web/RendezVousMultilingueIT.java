package be.autoservplus.reservation.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.ExportAgendaService;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un rendez-vous ne fige aucun libellé : ses prestations portent leur nom courant, dans la
 * langue de l'écran qui les montre, et dans celle du destinataire pour le fichier d'agenda
 * joint à un courriel. Sans traduction, le libellé français.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Rendez-vous : prestations dans la langue de l'écran ou du destinataire (intégration)")
class RendezVousMultilingueIT extends SocleIntegration {

    private static final Instant DEBUT = Instant.parse("2026-09-16T08:00:00Z");
    private static final String MEMBRE = "rdv-langue@exemple.be";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private ExportAgendaService exportAgenda;

    private Utilisateur membre;
    private Rdv rdv;

    @BeforeEach
    void semer() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        membre = utilisateurs.save(new Utilisateur(MEMBRE, "$2a$12$h", "Peeters", "An", TypeUtilisateur.MEMBRE));
        Vehicule golf = vehicules.save(new Vehicule(membre, "1-LNG-123", "Volkswagen", "Golf", Motorisation.DIESEL));

        Categorie entretien = new Categorie("IT-LNG-" + suffixe, "Entretien du test", TypeCategorie.SERVICE);
        entretien.traduire("Testonderhoud", null);
        categories.save(entretien);
        // Traduite en néerlandais seulement : l'anglais retombe sur le français.
        Prestation vidange = new Prestation(entretien, "IT-LNG-VID-" + suffixe, "Vidange du test",
                new BigDecimal("49.00"), 60);
        vidange.traduire(new Traductions("Olie verversen van de test", null, null, null));
        prestations.save(vidange);
        Prestation geometrie = prestations.save(new Prestation(entretien, "IT-LNG-GEO-" + suffixe,
                "Géométrie du test", new BigDecimal("80.00"), 30));

        PosteAtelier poste = postes.save(new PosteAtelier("Pont " + suffixe));
        Rdv nouveau = new Rdv("RDV-LNG-" + suffixe.substring(0, 4), membre, golf, poste, DEBUT,
                Duration.ofMinutes(90), List.of(vidange, geometrie), null);
        nouveau.confirmer(DEBUT.minus(Duration.ofDays(1)));
        rdv = rdvs.saveAndFlush(nouveau);
    }

    private String pageDuMembre(String adresse, String langue) throws Exception {
        return mvc.perform(get(adresse).param("lang", langue).with(user(MEMBRE).roles("MEMBRE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String pageDuGarage(String adresse, String langue) throws Exception {
        return mvc.perform(get(adresse).param("lang", langue)
                        .with(user("admin-langue@exemple.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("détail et liste du membre : traduction en néerlandais, français pour ce qui n'est pas traduit")
    void ecransDuMembre() throws Exception {
        String detail = pageDuMembre("/mes-rendez-vous/" + rdv.getReference(), "nl");
        assertThat(detail).contains("Olie verversen van de test").contains("Géométrie du test")
                .doesNotContain("Vidange du test");

        assertThat(pageDuMembre("/mes-rendez-vous", "nl")).contains("Olie verversen van de test");
        assertThat(pageDuMembre("/mes-rendez-vous/" + rdv.getReference(), "en")).contains("Vidange du test");
        assertThat(pageDuMembre("/mes-rendez-vous/" + rdv.getReference(), "fr")).contains("Vidange du test")
                .doesNotContain("Olie verversen");
    }

    @Test
    @DisplayName("formulaire de réservation : libellés dans la langue de l'écran, le repli déclaré en français")
    void formulaire() throws Exception {
        String neerlandais = pageDuMembre("/mes-rendez-vous/nouveau", "nl");
        assertThat(neerlandais).contains("<span>Olie verversen van de test</span>")
                .contains("<span lang=\"fr\">Géométrie du test</span>")
                .contains(">Testonderhoud</h2>");

        String anglais = pageDuMembre("/mes-rendez-vous/nouveau", "en");
        assertThat(anglais).contains("<span lang=\"fr\">Vidange du test</span>")
                .contains("<h2 lang=\"fr\">Entretien du test</h2>");

        assertThat(pageDuMembre("/mes-rendez-vous/nouveau", "fr"))
                .contains("<span>Vidange du test</span>").contains("<h2>Entretien du test</h2>");
    }

    @Test
    @DisplayName("back-office : détail et agenda dans la langue de l'administrateur")
    void ecransDuGarage() throws Exception {
        assertThat(pageDuGarage("/admin/rendez-vous/" + rdv.getReference(), "nl"))
                .contains("<li><span>Olie verversen van de test</span>")
                .contains("<li><span>Géométrie du test</span>");
        assertThat(pageDuGarage("/admin/rendez-vous/" + rdv.getReference(), "fr"))
                .contains("<li><span>Vidange du test</span>")
                .contains("<li><span>Géométrie du test</span>");

        String agenda = mvc.perform(get("/admin/rendez-vous/agenda").param("lang", "nl")
                        .param("du", "2026-09-16").param("au", "2026-09-16")
                        .with(user("admin-langue@exemple.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(agenda).contains("Olie verversen van de test, Géométrie du test");
    }

    @Test
    @DisplayName("fichier d'agenda téléchargé : la langue de l'écran")
    void fichierTelecharge() throws Exception {
        String ics = mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics", rdv.getReference()).param("lang", "nl")
                        .with(user(MEMBRE).roles("MEMBRE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(deplier(ics)).contains("Olie verversen van de test\\, Géométrie du test");
    }

    @Test
    @DisplayName("fichier joint au courriel : la langue du membre, pas celle de l'écran qui déclenche l'envoi")
    void fichierDuCourriel() {
        membre.setLangue(Langue.nl);
        assertThat(deplier(exportAgenda.pourLeCourriel(rdv).contenu()))
                .contains("Olie verversen van de test\\, Géométrie du test");

        membre.setLangue(Langue.en);
        assertThat(deplier(exportAgenda.pourLeCourriel(rdv).contenu()))
                .as("pas de traduction anglaise : le français")
                .contains("Vidange du test\\, Géométrie du test");
    }

    /** Annule le pliage à 75 octets de la RFC 5545 avant toute recherche de texte. */
    private static String deplier(String ics) {
        return ics.replace("\r\n ", "");
    }
}
