package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le calendrier vu du garage, en base réelle : déplacement d'un rendez-vous (sans le
 * délai minimal ni l'horizon du membre, motif obligatoire, rendez-vous confirmé à la
 * sortie) et rendez-vous tombant un jour férié, listés en tête des rendez-vous à traiter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AdminRdvDeplacementIT.HorlogeFixe.class)
@DisplayName("Rendez-vous : déplacement par le garage (intégration)")
class AdminRdvDeplacementIT extends SocleIntegration {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    // Samedi 12 septembre 2026, 09:00.
    private static final Instant MAINTENANT =
            LocalDate.of(2026, 9, 12).atTime(9, 0).atZone(BRUXELLES).toInstant();

    @TestConfiguration
    static class HorlogeFixe {
        @Bean
        @Primary
        Clock horlogeFigee() {
            return Clock.fixed(MAINTENANT, BRUXELLES);
        }
    }

    @MockitoBean private ServiceCourriel courriel;

    @Autowired private AdminRdvService adminRdvs;
    @Autowired private RdvService reservations;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private PlageOuvertureRepository plages;
    @Autowired private ParametreAtelierRepository parametres;
    @Autowired private IdentiteGarage garage;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    private Utilisateur marie;
    private Vehicule golf;
    private PosteAtelier pont;
    private Prestation vidange;

    @BeforeAll
    void atelierAUnPoste() {
        plages.deleteAllInBatch();
        postes.deleteAllInBatch();
        pont = postes.save(new PosteAtelier("Pont unique"));
        for (DayOfWeek jour : DayOfWeek.values()) {
            plages.save(new PlageOuverture(jour, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        marie = utilisateurs.save(new Utilisateur("marie.garage@exemple.be", "$2a$12$h",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        golf = vehicules.save(new Vehicule(marie, "1-GAR-001", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-GAR", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-GAR-VID", "Vidange", new BigDecimal("49.00"), 60));
    }

    @BeforeEach
    void administrateur() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin@garage.be", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR"))));
    }

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("une demande en attente déplacée à moins de 24 heures en sort confirmée, motif et téléphone au membre")
    void enAttenteSousLeDelaiMinimal() {
        confirmationAutomatique(false);
        Rdv rdv = reserver(le(2026, 9, 14, 10, 0));
        assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);

        Rdv deplace = adminRdvs.deplacer(rdv.getReference(), le(2026, 9, 12, 15, 0), "Pont libéré plus tôt",
                rdv.getVersion());

        assertThat(deplace.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        Rdv relu = rdvs.findByReference(rdv.getReference()).orElseThrow();
        assertThat(relu.getDebut()).isEqualTo(le(2026, 9, 12, 15, 0));
        assertThat(relu.getMotifRefus()).isNull();
        assertThat(jdbc.queryForObject("SELECT rappel_envoye_le IS NOT NULL FROM rdv WHERE id = ?",
                Boolean.class, rdv.getId()))
                .as("dans la fenêtre du rappel, le courriel de déplacement en tient lieu")
                .isTrue();
        verify(courriel).envoyerModificationParLeGarage(argThat(m -> m.getEmail().equals(marie.getEmail())),
                any(), any(), eq("Pont libéré plus tôt"), eq(garage.telephone()), any());
        assertThat(jdbc.queryForList(
                "SELECT type FROM notification WHERE membre_id = ? AND corps = ?",
                String.class, marie.getId(), rdv.getNumero()))
                .as("déplacé, et non confirmé, bien que la demande en sorte confirmée")
                .containsExactly("RDV_DEPLACE");
    }

    @Test
    @DisplayName("au-delà de l'horizon des membres, le garage déplace quand même")
    void auDelaDeLHorizon() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(le(2026, 9, 15, 10, 0));
        Instant decembre = le(2026, 12, 15, 10, 0);

        adminRdvs.deplacer(rdv.getReference(), decembre, "Pièce commandée", rdv.getVersion());

        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut()).isEqualTo(decembre);
    }

    @Test
    @DisplayName("sans motif, rien n'est écrit")
    void sansMotif() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(le(2026, 9, 16, 10, 0));

        assertThatThrownBy(() -> adminRdvs.deplacer(rdv.getReference(), le(2026, 9, 16, 14, 0), " ", rdv.getVersion()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut()).isEqualTo(le(2026, 9, 16, 10, 0));
    }

    @Test
    @DisplayName("un rendez-vous déjà commencé ne se déplace plus")
    void dejaCommence() {
        Rdv commence = new Rdv("RDV-IT-GAR-01", marie, golf, pont, MAINTENANT.minus(Duration.ofMinutes(30)),
                Duration.ofMinutes(30), List.of(vidange), null);
        commence.confirmer(MAINTENANT.minus(Duration.ofDays(1)));
        Rdv enregistre = rdvs.saveAndFlush(commence);

        assertThatThrownBy(() -> adminRdvs.deplacer(enregistre.getReference(), le(2026, 9, 13, 10, 0), "Report",
                enregistre.getVersion()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("un envoi fait sur une page périmée est refusé")
    void pagePerimee() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(le(2026, 9, 17, 10, 0));
        adminRdvs.deplacer(rdv.getReference(), le(2026, 9, 17, 11, 0), "Retard", rdv.getVersion());

        assertThatThrownBy(() -> adminRdvs.deplacer(rdv.getReference(), le(2026, 9, 17, 15, 0), "Retard",
                rdv.getVersion()))
                .isInstanceOf(ConflitConcurrenceException.class);
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut()).isEqualTo(le(2026, 9, 17, 11, 0));
    }

    @Test
    @DisplayName("un jour férié reste fermé au garage")
    void jourFerie() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(le(2026, 9, 18, 10, 0));

        assertThatThrownBy(() -> adminRdvs.deplacer(rdv.getReference(), le(2026, 11, 11, 10, 0), "Report",
                rdv.getVersion()))
                .isInstanceOf(CreneauIndisponibleException.class);
    }

    @Test
    @DisplayName("écran : refusé à un membre, affiché à l'administrateur, l'envoi déplace et revient au détail")
    void ecran() throws Exception {
        confirmationAutomatique(true);
        Rdv rdv = reserver(le(2026, 9, 19, 10, 0));

        // Jour et mois dans la langue de l'interface, pas en français.
        mvc.perform(get("/admin/rendez-vous/{ref}/modifier", rdv.getReference()).param("lang", "nl")
                        .with(user("admin@garage.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Huidig tijdslot: zaterdag 19 september 2026, van 10:00 tot 11:00, Marie Dupont.")));

        mvc.perform(get("/admin/rendez-vous/{ref}/modifier", rdv.getReference())
                        .with(user(marie.getEmail()).roles("MEMBRE")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/admin/rendez-vous/{ref}/modifier", rdv.getReference()).param("lang", "fr")
                        .with(user("admin@garage.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Déplacer le rendez-vous " + rdv.getNumero())));

        mvc.perform(post("/admin/rendez-vous/{ref}/modifier", rdv.getReference())
                        .param("date", "2026-09-19")
                        .param("debut", le(2026, 9, 19, 16, 0).toString())
                        .param("version", Long.toString(rdv.getVersion()))
                        .param("motif", "Pont en panne")
                        .param("lang", "fr")
                        .with(user("admin@garage.be").roles("ADMINISTRATEUR")).with(csrf()))
                .andExpect(redirectedUrl("/admin/rendez-vous/" + rdv.getReference()))
                .andExpect(flash().attribute("message", "Le rendez-vous " + rdv.getNumero() + " est déplacé et confirmé."));
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut())
                .isEqualTo(le(2026, 9, 19, 16, 0));
    }

    @Test
    @DisplayName("jours fériés : les rendez-vous non terminés à venir sont listés sans borne, ni annulés ni passés")
    void rendezVousJoursFeries() throws Exception {
        Rdv toussaint = poser("RDV-IT-FER-01", le(2026, 11, 1, 10, 0));
        Rdv nouvelAn = poser("RDV-IT-FER-02", le(2027, 1, 1, 10, 0));
        nouvelAn.confirmer(MAINTENANT);
        rdvs.saveAndFlush(nouvelAn);
        Rdv annule = poser("RDV-IT-FER-03", le(2026, 11, 11, 10, 0));
        annule.annulerParLeMembre(MAINTENANT, Duration.ofHours(24));
        rdvs.saveAndFlush(annule);
        Rdv passe = new Rdv("RDV-IT-FER-04", marie, golf, pont, le(2026, 8, 15, 10, 0),
                Duration.ofMinutes(30), List.of(vidange), null);
        passe.confirmer(le(2026, 8, 1, 10, 0));
        rdvs.saveAndFlush(passe);
        poser("RDV-IT-FER-05", le(2026, 11, 2, 10, 0));

        assertThat(adminRdvs.rendezVousJoursFeries())
                .extracting(vue -> vue.numero())
                .as("au-delà de soixante jours compris, sans l'annulé, le passé ni le jour ouvrable")
                .containsExactly(toussaint.getNumero(), nouvelAn.getNumero());

        mvc.perform(get("/admin/rendez-vous").param("lang", "fr")
                        .with(user("admin@garage.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rendez-vous un jour férié")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(toussaint.getNumero())));
        mvc.perform(get("/admin/rendez-vous/agenda").param("du", "2026-11-01").param("au", "2026-11-02")
                        .param("lang", "fr").with(user("admin@garage.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Jour férié")));
    }

    // --- outillage ---------------------------------------------------------------------

    /** Rendez-vous écrit hors réservation : un jour férié n'est pas réservable. */
    private Rdv poser(String numero, Instant debut) {
        return rdvs.saveAndFlush(new Rdv(numero, marie, golf, pont, debut, Duration.ofMinutes(30),
                List.of(vidange), null));
    }

    private static Instant le(int annee, int mois, int jour, int heure, int minute) {
        return LocalDateTime.of(annee, mois, jour, heure, minute).atZone(BRUXELLES).toInstant();
    }

    private Rdv reserver(Instant debut) {
        return reservations.reserver(marie.getEmail(), golf.getReference(),
                List.of(vidange.getReference()), debut, null);
    }

    private void confirmationAutomatique(boolean automatique) {
        ParametreAtelier p = parametres.courants();
        p.modifierReservation(automatique, p.getDelaiReponseHeures(), p.getDelaiMinimalHeures(),
                p.getDelaiAnnulationHeures(), p.getDelaiRappelHeures());
        parametres.saveAndFlush(p);
    }
}
