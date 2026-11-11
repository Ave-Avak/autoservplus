package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Avis d'une nouvelle demande, rappel avant le rendez-vous et annulation par le membre,
 * contre une base réelle (F16).
 *
 * <p>Non transactionnelle : la réservation du rappel est une mise à jour validée pour
 * de bon, et deux passages de la tâche doivent la voir. Le service de courriel est
 * remplacé par un bouchon dont on compte les appels. Chaque test prend son propre
 * jour ou sa propre heure.</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AvisRendezVousIT.HorlogeFixe.class)
@DisplayName("Rendez-vous : avis de demande, rappel et annulation par le membre (intégration)")
class AvisRendezVousIT extends SocleIntegration {

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

    @Autowired private RdvService reservations;
    @Autowired private TachesRendezVous taches;
    @Autowired private RdvRepository rdvs;
    @Autowired private ParametreAtelierRepository parametres;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private PlageOuvertureRepository plages;
    @Autowired private JdbcTemplate jdbc;

    private Utilisateur marie;
    private Utilisateur admin;
    private Utilisateur paul;
    private Vehicule golf;
    private Vehicule clio;
    private PosteAtelier pont;
    private Prestation vidange;

    @BeforeAll
    void atelier() {
        plages.deleteAllInBatch();
        postes.deleteAllInBatch();
        pont = postes.save(new PosteAtelier("Pont des avis"));
        for (DayOfWeek jour : DayOfWeek.values()) {
            plages.save(new PlageOuverture(jour, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        marie = utilisateurs.save(new Utilisateur("marie.avis@exemple.be", "$2a$12$h",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Utilisateur administrateur = new Utilisateur("admin.avis@exemple.be", "$2a$12$h",
                "Atelier", "Chef", TypeUtilisateur.ADMINISTRATEUR);
        administrateur.setStatut(StatutUtilisateur.ACTIF);
        admin = utilisateurs.save(administrateur);
        golf = vehicules.save(new Vehicule(marie, "1-AVI-001", "VW", "Golf", Motorisation.DIESEL));
        // Les demandes insérées directement sont à Paul : Marie garde sa marge sous le
        // plafond de demandes en attente pour les réservations par le service.
        paul = utilisateurs.save(new Utilisateur("paul.avis@exemple.be", "$2a$12$h",
                "Martin", "Paul", TypeUtilisateur.MEMBRE));
        clio = vehicules.save(new Vehicule(paul, "1-AVI-002", "Renault", "Clio", Motorisation.ESSENCE));
        Categorie entretien = categories.save(new Categorie("IT-AVI", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-AVI-VID", "Vidange", new BigDecimal("49.00"), 60));
        // Mode manuel : les demandes restent en attente et portent une échéance.
        modifierParametres(false, 24);
    }

    @AfterEach
    void parametresParDefaut() {
        modifierParametres(false, 24);
    }

    @Test
    @DisplayName("une demande en attente : accusé au membre, courriel et notification au garage")
    void nouvelleDemandeEnAttente() {
        Rdv rdv = reserver(LocalDate.of(2026, 9, 13).atTime(10, 0));

        verify(courriel).envoyerAccuseDemandeRdv(argThat(m -> m.getEmail().equals(marie.getEmail())),
                argThat(d -> d.numero().equals(rdv.getNumero())), eq(false), eq(24));
        verify(courriel).envoyerAvisNouvelleDemandeAuGarage(argThat(d -> d.numero().equals(rdv.getNumero())),
                eq("Marie Dupont"), eq(false), eq("/admin/rendez-vous/" + rdv.getReference()));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE type = 'RDV_DEMANDE' AND membre_id = ? AND corps = ?",
                Long.class, admin.getId(), rdv.getNumero())).isEqualTo(1L);
    }

    @Test
    @DisplayName("une demande confirmée d'office est annoncée confirmée au membre et au garage")
    void nouvelleDemandeConfirmee() {
        modifierParametres(true, 24);

        Rdv rdv = reserver(LocalDate.of(2026, 9, 14).atTime(10, 0));

        verify(courriel).envoyerAccuseDemandeRdv(any(), argThat(d -> d.numero().equals(rdv.getNumero())), eq(true), anyInt());
        verify(courriel).envoyerAvisNouvelleDemandeAuGarage(any(), any(), eq(true), any());
    }

    @Test
    @DisplayName("l'annulation par le membre lui est confirmée par courriel")
    void annulationParLeMembre() {
        Rdv rdv = reserver(LocalDate.of(2026, 9, 20).atTime(10, 0));

        reservations.annuler(rdv.getReference(), marie.getEmail());

        verify(courriel).envoyerAnnulationParLeMembre(argThat(m -> m.getEmail().equals(marie.getEmail())),
                argThat(d -> d.numero().equals(rdv.getNumero())));
    }

    @Test
    @DisplayName("le rappel part une fois dans le délai, jamais au-delà ni au second passage")
    void rappelUneSeuleFois() {
        Rdv proche = confirme("RDV-AVI-0001", MAINTENANT.plus(Duration.ofHours(10)));
        Rdv lointain = confirme("RDV-AVI-0002", MAINTENANT.plus(Duration.ofHours(30)));

        assertThat(taches.envoyerLesRappels()).isEqualTo(1);
        assertThat(taches.envoyerLesRappels()).as("second passage").isZero();

        verify(courriel, times(1)).envoyerRappelRdv(any(), argThat(d -> d.numero().equals(proche.getNumero())));
        verify(courriel, never()).envoyerRappelRdv(any(), argThat(d -> d.numero().equals(lointain.getNumero())));
        assertThat(rdvs.findByReference(proche.getReference()).orElseThrow().getRappelEnvoyeLe()).isEqualTo(MAINTENANT);
        assertThat(rdvs.findByReference(lointain.getReference()).orElseThrow().getRappelEnvoyeLe()).isNull();
    }

    @Test
    @DisplayName("deux passages simultanés de la tâche n'envoient le rappel qu'une fois")
    void rappelPassagesSimultanes() throws Exception {
        Rdv rdv = confirme("RDV-AVI-0003", MAINTENANT.plus(Duration.ofHours(12)));

        ExecutorService fils = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            Future<Integer> premier = fils.submit(() -> { depart.await(); return taches.envoyerLesRappels(); });
            Future<Integer> second = fils.submit(() -> { depart.await(); return taches.envoyerLesRappels(); });
            depart.countDown();
            assertThat(premier.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            fils.shutdownNow();
        }
        verify(courriel, times(1)).envoyerRappelRdv(any(), argThat(d -> d.numero().equals(rdv.getNumero())));
    }

    @Test
    @DisplayName("un délai de rappel nul désactive le rappel")
    void rappelDesactive() {
        modifierParametres(false, 0);
        Rdv rdv = confirme("RDV-AVI-0004", MAINTENANT.plus(Duration.ofHours(2)));

        assertThat(taches.envoyerLesRappels()).isZero();

        verify(courriel, never()).envoyerRappelRdv(any(), any());
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getRappelEnvoyeLe()).isNull();
        rdvs.delete(rdvs.findByReference(rdv.getReference()).orElseThrow());
    }

    @Test
    @DisplayName("une demande en mode manuel porte son échéance : midi du samedi pour un dimanche")
    void echeanceFigeeALaCreation() {
        Rdv rdv = reserver(LocalDate.of(2026, 9, 13).atTime(13, 0));

        assertThat(rdv.getEcheanceReponse())
                .isEqualTo(LocalDate.of(2026, 9, 12).atTime(12, 0).atZone(BRUXELLES).toInstant());
    }

    @Test
    @DisplayName("une demande échue expire une fois, le membre est prévenu, une demande à venir reste")
    void expiration() {
        Rdv echue = enAttente("RDV-AVI-0101", MAINTENANT.plus(Duration.ofDays(3)), MAINTENANT.minusSeconds(60));
        Rdv aVenir = enAttente("RDV-AVI-0102", MAINTENANT.plus(Duration.ofDays(4)), MAINTENANT.plus(Duration.ofHours(3)));

        assertThat(taches.expirerLesDemandes()).isGreaterThanOrEqualTo(1);
        assertThat(taches.expirerLesDemandes()).as("second passage").isZero();

        assertThat(rdvs.findByReference(echue.getReference()).orElseThrow().getStatut()).isEqualTo(StatutRdv.EXPIRE);
        assertThat(rdvs.findByReference(aVenir.getReference()).orElseThrow().getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        verify(courriel, times(1)).envoyerExpirationRdv(any(), argThat(d -> d.numero().equals(echue.getNumero())));
        verify(courriel, never()).envoyerExpirationRdv(any(), argThat(d -> d.numero().equals(aVenir.getNumero())));
    }

    @Test
    @DisplayName("relance au garage à mi-délai, une seule fois, courriel et notification")
    void relanceAMiDelai() {
        Rdv rdv = enAttente("RDV-AVI-0201", MAINTENANT.plus(Duration.ofDays(5)), MAINTENANT.plus(Duration.ofHours(2)));
        jdbc.update("UPDATE rdv SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(MAINTENANT.minus(Duration.ofHours(4))), rdv.getId());

        assertThat(taches.relancerLeGarage()).isEqualTo(1);
        assertThat(taches.relancerLeGarage()).as("second passage").isZero();

        verify(courriel, times(1)).envoyerRelanceAuGarage(argThat(d -> d.numero().equals(rdv.getNumero())),
                eq("Paul Martin"), any(), eq("/admin/rendez-vous/" + rdv.getReference()));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE type = 'RDV_RELANCE' AND membre_id = ? AND corps = ?",
                Long.class, admin.getId(), rdv.getNumero())).isEqualTo(1L);
        assertThat(rdvs.findByReference(rdv.getReference()).orElseThrow().getRelanceEnvoyeeLe()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("avant mi-délai, aucune relance")
    void pasDeRelanceAvantMiDelai() {
        Rdv rdv = enAttente("RDV-AVI-0202", MAINTENANT.plus(Duration.ofDays(6)), MAINTENANT.plus(Duration.ofHours(10)));

        taches.relancerLeGarage();

        verify(courriel, never()).envoyerRelanceAuGarage(argThat(d -> d.numero().equals(rdv.getNumero())),
                any(), any(), any());
    }

    // --- outillage ---------------------------------------------------------------------

    private Rdv reserver(LocalDateTime debutLocal) {
        return reservations.reserver(marie.getEmail(), golf.getReference(),
                List.of(vidange.getReference()), debutLocal.atZone(BRUXELLES).toInstant(), null);
    }

    private Rdv confirme(String numero, Instant debut) {
        Rdv rdv = new Rdv(numero, marie, golf, pont, debut, Duration.ofMinutes(30), List.of(vidange), null);
        rdv.confirmer(MAINTENANT.minus(Duration.ofDays(1)));
        return rdvs.saveAndFlush(rdv);
    }

    private Rdv enAttente(String numero, Instant debut, Instant echeance) {
        Rdv rdv = new Rdv(numero, paul, clio, pont, debut, Duration.ofMinutes(30), List.of(vidange), null);
        rdv.fixerEcheanceReponse(echeance);
        return rdvs.saveAndFlush(rdv);
    }

    private void modifierParametres(boolean confirmationAutomatique, int delaiRappelHeures) {
        ParametreAtelier p = parametres.courants();
        p.modifier(p.getFuseauHoraire(), p.getPasMinutes(), p.getTamponMinutes(), p.getDelaiMinimalHeures(),
                p.getHorizonJours(), p.getDelaiAnnulationHeures(), confirmationAutomatique,
                p.getMaxRdvEnAttenteParMembre());
        p.modifierRappel(delaiRappelHeures);
        parametres.saveAndFlush(p);
    }
}
