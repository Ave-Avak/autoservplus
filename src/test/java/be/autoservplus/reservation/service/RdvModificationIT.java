package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
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
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
 * Déplacement d'un rendez-vous par le membre, en base réelle.
 *
 * <p>Un seul poste et des plages de 08:00 à 18:00 tous les jours : un nouveau créneau
 * qui recoupe l'ancien vise forcément la même ligne de l'index d'exclusion, ce qui
 * prouve que la contrainte accepte le déplacement d'un rendez-vous sur sa propre
 * position. La classe n'est pas transactionnelle : chaque test prend ses propres jours.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(RdvModificationIT.HorlogeFixe.class)
@DisplayName("Rendez-vous : déplacement par le membre (intégration)")
class RdvModificationIT extends SocleIntegration {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    // Samedi 12 septembre 2026, 09:00. Premier créneau réservable : dimanche 09:00.
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

    @Autowired private RdvService reservations;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private PlageOuvertureRepository plages;
    @Autowired private ParametreAtelierRepository parametres;
    @Autowired private TransactionTemplate transaction;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    private Utilisateur marie;
    private Utilisateur paul;
    private Utilisateur lea;
    private Utilisateur admin;
    private Vehicule golf;
    private Vehicule clio;
    private Vehicule polo;
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
        marie = utilisateurs.save(new Utilisateur("marie.deplacement@exemple.be", "$2a$12$h",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        paul = utilisateurs.save(new Utilisateur("paul.deplacement@exemple.be", "$2a$12$h",
                "Martin", "Paul", TypeUtilisateur.MEMBRE));
        lea = utilisateurs.save(new Utilisateur("lea.deplacement@exemple.be", "$2a$12$h",
                "Leroy", "Léa", TypeUtilisateur.MEMBRE));
        Utilisateur administrateur = new Utilisateur("admin.deplacement@exemple.be", "$2a$12$h",
                "Garage", "Admin", TypeUtilisateur.ADMINISTRATEUR);
        administrateur.setStatut(StatutUtilisateur.ACTIF);
        admin = utilisateurs.save(administrateur);
        golf = vehicules.save(new Vehicule(marie, "1-DEP-001", "VW", "Golf", Motorisation.DIESEL));
        clio = vehicules.save(new Vehicule(paul, "1-DEP-002", "Renault", "Clio", Motorisation.ESSENCE));
        polo = vehicules.save(new Vehicule(lea, "1-DEP-003", "VW", "Polo", Motorisation.ESSENCE));
        Categorie entretien = categories.save(new Categorie("IT-DEP", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-DEP-VID", "Vidange", new BigDecimal("49.00"), 60));
    }

    @Test
    @DisplayName("un nouveau créneau qui recoupe l'ancien sur le même poste est accepté par la contrainte d'exclusion")
    void recoupeLAncienCreneau() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(14, 10, 0));

        Rdv deplace = reservations.modifier(rdv.getReference(), marie.getEmail(), le(14, 10, 30), rdv.getVersion());

        Rdv relu = rdvs.findByReference(rdv.getReference()).orElseThrow();
        assertThat(relu.getDebut()).isEqualTo(le(14, 10, 30));
        assertThat(relu.getFin()).isEqualTo(le(14, 11, 30));
        assertThat(relu.getPoste()).isEqualTo(pont);
        assertThat(relu.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(relu.getNumero()).isEqualTo(rdv.getNumero());
        assertThat(relu.getVersion()).isGreaterThan(rdv.getVersion());
        verify(courriel).envoyerModificationRdvConfirmee(eq(marie), any(), any(), any());
        verify(courriel).envoyerAvisModificationAuGarage(any(), any(), eq("Marie Dupont"), eq(true),
                eq("/admin/rendez-vous/" + deplace.getReference()));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE type = 'RDV_MODIFIE' AND membre_id = ? AND corps = ?",
                Long.class, admin.getId(), rdv.getNumero())).isEqualTo(1L);
    }

    @Test
    @DisplayName("mode manuel : un rendez-vous confirmé repasse en attente, avec une nouvelle échéance et la relance remise à zéro")
    void manuelRepasseEnAttente() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(15, 10, 0));
        jdbc.update("UPDATE rdv SET relance_envoyee_le = now() WHERE id = ?", rdv.getId());
        confirmationAutomatique(false);

        reservations.modifier(rdv.getReference(), marie.getEmail(), le(16, 10, 0), rdv.getVersion());

        Rdv relu = rdvs.findByReference(rdv.getReference()).orElseThrow();
        assertThat(relu.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        assertThat(relu.getEcheanceReponse()).isNotNull().isBefore(le(16, 10, 0));
        assertThat(relu.getRelanceEnvoyeeLe()).isNull();
        verify(courriel).envoyerModificationRdvEnAttente(eq(marie), any(), any(), any());
    }

    @Test
    @DisplayName("mode manuel : une demande ancienne déplacée n'est pas relancée aussitôt, la mi-délai part du déplacement")
    void relanceDUneDemandeAncienneDeplacee() {
        confirmationAutomatique(false);
        Rdv rdv = reserver(marie, golf, le(30, 10, 0));
        // La demande date de dix jours : mesurée depuis sa création, sa mi-délai serait
        // passée depuis longtemps.
        Instant ilYADixJours = MAINTENANT.minus(Duration.ofDays(10));
        jdbc.update("UPDATE rdv SET created_at = ?, en_attente_depuis = ? WHERE id = ?",
                java.sql.Timestamp.from(ilYADixJours), java.sql.Timestamp.from(ilYADixJours), rdv.getId());

        reservations.modifier(rdv.getReference(), marie.getEmail(), le(30, 14, 0), rdv.getVersion());

        Rdv relu = rdvs.findByReference(rdv.getReference()).orElseThrow();
        assertThat(relu.getEnAttenteDepuis()).isEqualTo(MAINTENANT);
        assertThat(rdvs.relancesDues(MAINTENANT)).extracting(Rdv::getReference)
                .doesNotContain(rdv.getReference());
        assertThat(jdbc.queryForObject("""
                SELECT created_at + (echeance_reponse - created_at) / 2 <= ? FROM rdv WHERE id = ?""",
                Boolean.class, java.sql.Timestamp.from(MAINTENANT), rdv.getId()))
                .as("mesurée depuis la création, la relance serait partie")
                .isTrue();
        Instant miDelai = MAINTENANT.plus(Duration.between(MAINTENANT, relu.getEcheanceReponse()).dividedBy(2));
        assertThat(rdvs.relancesDues(miDelai)).extracting(Rdv::getReference).contains(rdv.getReference());
    }

    @Test
    @DisplayName("le rappel déjà réservé est remis à zéro, puis se réserve de nouveau pour le nouveau créneau")
    void rappelRemisAZero() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(17, 10, 0));
        jdbc.update("UPDATE rdv SET rappel_envoye_le = now() WHERE id = ?", rdv.getId());

        reservations.modifier(rdv.getReference(), marie.getEmail(), le(18, 10, 0), rdv.getVersion());

        assertThat(jdbc.queryForObject("SELECT rappel_envoye_le IS NULL FROM rdv WHERE id = ?",
                Boolean.class, rdv.getId())).isTrue();
        Integer reserve = transaction.execute(statut -> rdvs.reserverRappel(rdv.getId(), MAINTENANT));
        assertThat(reserve).as("le rappel du nouveau créneau peut partir").isEqualTo(1);
    }

    @Test
    @DisplayName("refusé à moins de 24 heures de l'ancien début (RM-11), rien n'est écrit")
    void delaiDepasse() {
        confirmationAutomatique(true);
        Rdv proche = new Rdv("RDV-IT-DEP-01", marie, golf, pont, MAINTENANT.plus(Duration.ofHours(12)),
                Duration.ofMinutes(30), List.of(vidange), null);
        proche.confirmer(MAINTENANT);
        Rdv enregistre = rdvs.saveAndFlush(proche);

        assertThatThrownBy(() -> reservations.modifier(enregistre.getReference(), marie.getEmail(),
                le(19, 10, 0), enregistre.getVersion()))
                .isInstanceOfSatisfying(RefusTraduit.class, refus -> {
                    assertThat(refus.getCodeRegle()).isEqualTo("RM-11");
                    assertThat(refus.getCleMessage()).isEqualTo("rdv.modification.delai");
                });
        assertThat(rdvs.findByReference(enregistre.getReference()).orElseThrow().getDebut())
                .isEqualTo(enregistre.getDebut());
    }

    @Test
    @DisplayName("le créneau d'un autre membre reste pris")
    void creneauDAutrui() {
        confirmationAutomatique(true);
        reserver(paul, clio, le(21, 10, 0));
        Rdv rdv = reserver(marie, golf, le(21, 14, 0));

        assertThatThrownBy(() -> reservations.modifier(rdv.getReference(), marie.getEmail(),
                le(21, 10, 30), rdv.getVersion()))
                .isInstanceOf(CreneauIndisponibleException.class);
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut()).isEqualTo(le(21, 14, 0));
    }

    @Test
    @DisplayName("un jour férié est refusé")
    void jourFerie() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(22, 10, 0));
        Instant toussaint = LocalDate.of(2026, 11, 1).atTime(10, 0).atZone(BRUXELLES).toInstant();

        assertThatThrownBy(() -> reservations.modifier(rdv.getReference(), marie.getEmail(),
                toussaint, rdv.getVersion()))
                .isInstanceOf(CreneauIndisponibleException.class);
    }

    @Test
    @DisplayName("un envoi fait sur une page périmée est refusé, le premier déplacement reste")
    void pagePerimee() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(23, 10, 0));
        long versionAffichee = rdv.getVersion();
        reservations.modifier(rdv.getReference(), marie.getEmail(), le(23, 11, 0), versionAffichee);

        assertThatThrownBy(() -> reservations.modifier(rdv.getReference(), marie.getEmail(),
                le(23, 15, 0), versionAffichee))
                .isInstanceOfSatisfying(RefusTraduit.class,
                        refus -> assertThat(refus.getCleMessage()).isEqualTo("rdv.modification.perimee"));
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut()).isEqualTo(le(23, 11, 0));
    }

    @Test
    @DisplayName("deux déplacements simultanés : le verrou de version n'en laisse passer qu'un")
    void deplacementsSimultanes() throws Exception {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(24, 10, 0));
        long version = rdv.getVersion();

        List<Object> issues = enMemeTemps(
                () -> reservations.modifier(rdv.getReference(), marie.getEmail(), le(24, 12, 0), version),
                () -> reservations.modifier(rdv.getReference(), marie.getEmail(), le(24, 15, 0), version));

        assertThat(issues).filteredOn(Rdv.class::isInstance).hasSize(1);
        assertThat(issues).filteredOn(RefusTraduit.class::isInstance).singleElement()
                .extracting(refus -> ((RefusTraduit) refus).getCleMessage())
                .isEqualTo("rdv.modification.perimee");
        Rdv gagnant = (Rdv) issues.stream().filter(Rdv.class::isInstance).findFirst().orElseThrow();
        Rdv relu = rdvs.findByReference(rdv.getReference()).orElseThrow();
        assertThat(relu.getDebut()).isEqualTo(gagnant.getDebut());
        assertThat(relu.getVersion()).isEqualTo(gagnant.getVersion());
    }

    @Test
    @DisplayName("le rendez-vous d'un autre membre est introuvable")
    void rendezVousDAutrui() {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(25, 10, 0));

        assertThatThrownBy(() -> reservations.modifier(rdv.getReference(), paul.getEmail(),
                le(25, 14, 0), rdv.getVersion()))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("mode manuel : déplacer une demande ne compte pas dans le plafond des demandes en attente")
    void horsPlafond() {
        confirmationAutomatique(false);
        Rdv premiere = reserver(lea, polo, le(26, 10, 0));
        reserver(lea, polo, le(27, 10, 0));
        reserver(lea, polo, le(28, 10, 0));
        assertThat(rdvs.countByMembreEmailAndStatut(lea.getEmail(), StatutRdv.EN_ATTENTE)).isEqualTo(3);

        reservations.modifier(premiere.getReference(), lea.getEmail(), le(26, 14, 0), premiere.getVersion());

        assertThat(rdvs.findByReference(premiere.getReference()).orElseThrow().getDebut()).isEqualTo(le(26, 14, 0));
    }

    @Test
    @DisplayName("écran : la page s'affiche traduite, l'envoi déplace et revient au détail avec un message")
    void ecran() throws Exception {
        confirmationAutomatique(true);
        Rdv rdv = reserver(marie, golf, le(29, 10, 0));

        mvc.perform(get("/mes-rendez-vous/{ref}/modifier", rdv.getReference())
                        .param("date", "2026-09-29").param("lang", "nl")
                        .with(user(marie.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Afspraak " + rdv.getNumero() + " verplaatsen")))
                // Jour et mois dans la langue de l'interface, pas en français.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Huidig tijdslot: dinsdag 29 september 2026, van 10:00 tot 11:00.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"version\" value=\"" + rdv.getVersion() + "\"")));

        mvc.perform(post("/mes-rendez-vous/{ref}/modifier", rdv.getReference())
                        .param("date", "2026-09-29")
                        .param("debut", le(29, 15, 0).toString())
                        .param("version", Long.toString(rdv.getVersion()))
                        .with(user(marie.getEmail())).with(csrf()))
                .andExpect(redirectedUrl("/mes-rendez-vous/" + rdv.getReference()))
                .andExpect(flash().attributeExists("message"));
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getDebut()).isEqualTo(le(29, 15, 0));

        mvc.perform(post("/mes-rendez-vous/{ref}/modifier", rdv.getReference())
                        .param("date", "2026-09-29")
                        .param("debut", le(29, 16, 0).toString())
                        .param("version", Long.toString(rdv.getVersion()))
                        .param("lang", "fr")
                        .with(user(marie.getEmail())).with(csrf()))
                .andExpect(redirectedUrl("/mes-rendez-vous/" + rdv.getReference()))
                // Message sans argument : son apostrophe simple doit arriver intacte.
                .andExpect(flash().attribute("erreur",
                        "Le rendez-vous a changé depuis l'ouverture de cette page. Vérifiez-le avant de le déplacer."));
    }

    // --- outillage ---------------------------------------------------------------------

    private static Instant le(int jourDeSeptembre, int heure, int minute) {
        return LocalDateTime.of(2026, 9, jourDeSeptembre, heure, minute).atZone(BRUXELLES).toInstant();
    }

    private Rdv reserver(Utilisateur membre, Vehicule vehicule, Instant debut) {
        return reservations.reserver(membre.getEmail(), vehicule.getReference(),
                List.of(vidange.getReference()), debut, null);
    }

    private void confirmationAutomatique(boolean automatique) {
        ParametreAtelier p = parametres.courants();
        p.modifierReservation(automatique, p.getDelaiReponseHeures(), p.getDelaiMinimalHeures(),
                p.getDelaiAnnulationHeures(), p.getDelaiRappelHeures());
        parametres.saveAndFlush(p);
    }

    @SafeVarargs
    private List<Object> enMemeTemps(java.util.concurrent.Callable<?>... actions) throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService fils = Executors.newFixedThreadPool(actions.length);
        try {
            List<Future<?>> futurs = new ArrayList<>();
            for (var action : actions) {
                futurs.add(fils.submit(() -> {
                    if (!depart.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Départ non donné en trente secondes");
                    }
                    return action.call();
                }));
            }
            depart.countDown();
            List<Object> issues = new ArrayList<>();
            for (Future<?> futur : futurs) {
                try {
                    issues.add(futur.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    issues.add(e.getCause());
                }
            }
            return issues;
        } finally {
            fils.shutdownNow();
        }
    }
}
