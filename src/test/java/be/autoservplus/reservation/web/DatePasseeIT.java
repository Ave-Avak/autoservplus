package be.autoservplus.reservation.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.CreneauIndisponibleException;
import be.autoservplus.reservation.service.DisponibiliteService;
import be.autoservplus.reservation.service.RdvService;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un membre ne réserve ni ne déplace un rendez-vous dans le passé, de la requête HTTP à la
 * base. La règle vit dans {@code DisponibiliteService.dansLaFenetre} et ses tests unitaires
 * la couvrent, mais aucun test ne soumettait une date passée par le formulaire : un
 * contrôle oublié entre le formulaire et le service ne se serait vu nulle part.
 *
 * <p>L'heure soumise est celle d'un créneau ouvert, reculée de deux semaines : même jour de
 * la semaine, même heure, dans les plages d'ouverture. Seul le fait d'être passée la rend
 * irrecevable.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Date passée refusée au membre, du formulaire à la base (intégration)")
class DatePasseeIT extends SocleIntegration {

    private static final String EMAIL = "date-passee@exemple.be";
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private DisponibiliteService disponibilites;
    @Autowired private RdvService rdvs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock horloge;

    private Long idMembre;
    private Vehicule golf;
    private Prestation vidange;
    private CreneauDisponible creneauOuvert;

    @BeforeEach
    void setUp() {
        var membre = utilisateurs.save(MembresDeTest.membre(EMAIL, "$2a$12$h", "Dupont", "Marie"));
        idMembre = membre.getId();
        golf = vehicules.save(new Vehicule(membre, "1-DPA-001", "VW", "Golf", Motorisation.DIESEL));
        Categorie atelier = categories.save(new Categorie("IT-DPA", "Atelier date passée", TypeCategorie.SERVICE));
        vidange = prestations.saveAndFlush(new Prestation(atelier, "IT-DPA-VID", "Vidange date passée",
                new BigDecimal("69.00"), 30));
        creneauOuvert = premierCreneauOuvert();
    }

    private CreneauDisponible premierCreneauOuvert() {
        for (int jours = 2; jours < 30; jours++) {
            List<CreneauDisponible> libres = disponibilites.creneauxDuJour(
                    LocalDate.now(horloge).plusDays(jours), vidange.getDureeMinutes());
            if (!libres.isEmpty()) {
                return libres.get(0);
            }
        }
        throw new AssertionError("aucun jour ouvert dans le mois");
    }

    private Instant ilYADeuxSemaines() {
        Instant passe = creneauOuvert.debut().minus(Duration.ofDays(14));
        assertThat(passe).as("l'heure soumise est passée").isBefore(horloge.instant());
        return passe;
    }

    private long rendezVousDuMembre() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM rdv WHERE membre_id = ?", Long.class, idMembre);
    }

    @Test
    @DisplayName("réservation à une date passée : refus sous l'heure, aucun rendez-vous écrit")
    void reservationRefusee() throws Exception {
        Instant passe = ilYADeuxSemaines();

        mvc.perform(post("/mes-rendez-vous/nouveau").with(user(EMAIL).roles("MEMBRE")).with(csrf())
                        .locale(Locale.FRENCH)
                        .param("vehicule", golf.getReference().toString())
                        .param("prestations", vidange.getReference().toString())
                        .param("date", passe.atZone(BRUXELLES).toLocalDate().toString())
                        .param("debut", passe.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("formulaire", "debut",
                        CreneauIndisponibleException.NON_OUVERT));

        assertThat(rendezVousDuMembre()).isZero();
    }

    @Test
    @DisplayName("réservation une minute avant maintenant : même refus")
    void reservationJusteAvantMaintenantRefusee() throws Exception {
        Instant passe = horloge.instant().minus(Duration.ofMinutes(1));

        mvc.perform(post("/mes-rendez-vous/nouveau").with(user(EMAIL).roles("MEMBRE")).with(csrf())
                        .locale(Locale.FRENCH)
                        .param("vehicule", golf.getReference().toString())
                        .param("prestations", vidange.getReference().toString())
                        .param("date", passe.atZone(BRUXELLES).toLocalDate().toString())
                        .param("debut", passe.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("formulaire", "debut",
                        CreneauIndisponibleException.NON_OUVERT));

        assertThat(rendezVousDuMembre()).isZero();
    }

    @Test
    @DisplayName("déplacement vers une date passée : refus sur l'écran, rendez-vous inchangé")
    void deplacementRefuse() throws Exception {
        Rdv rdv = rdvs.reserver(EMAIL, golf.getReference(), List.of(vidange.getReference()),
                creneauOuvert.debut(), null);
        entityManager.flush();
        Instant passe = ilYADeuxSemaines();

        mvc.perform(post("/mes-rendez-vous/" + rdv.getReference() + "/modifier")
                        .with(user(EMAIL).roles("MEMBRE")).with(csrf())
                        .locale(Locale.FRENCH)
                        .param("date", passe.atZone(BRUXELLES).toLocalDate().toString())
                        .param("debut", passe.toString())
                        .param("version", String.valueOf(rdv.getVersion())))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("erreurHeure"));

        entityManager.flush();
        Timestamp debutEnBase = jdbc.queryForObject("SELECT debut FROM rdv WHERE id = ?", Timestamp.class, rdv.getId());
        assertThat(debutEnBase.toInstant()).isEqualTo(creneauOuvert.debut());
        assertThat(rendezVousDuMembre()).isEqualTo(1);
    }
}
