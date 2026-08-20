package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reservation de rendez-vous sur un PostgreSQL reel.
 *
 * <p>Le but est de prouver ce que les tests unitaires ne peuvent pas : que la contrainte
 * d exclusion rejette effectivement deux rendez-vous chevauchants sur un meme poste, et
 * que le service traduit ce rejet en regle metier. Chaque test s execute dans une
 * transaction annulee a la fin, la base revient vierge entre deux methodes.</p>
 */
@SpringBootTest
@Testcontainers
@Transactional
@DisplayName("RdvService (integration)")
class RdvServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private RdvService service;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private PlageOuvertureRepository plages;

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    private Utilisateur marie;
    private Utilisateur paul;
    private Vehicule golf;
    private Vehicule clio;
    private Prestation vidange;
    private PosteAtelier pont1;
    private Instant lundiProchain10h;

    @BeforeEach
    void setUp() {
        marie = utilisateurs.save(new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        paul = utilisateurs.save(new Utilisateur("paul@exemple.be", "$2a$12$h", "Martin", "Paul", TypeUtilisateur.MEMBRE));
        golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
        clio = vehicules.save(new Vehicule(paul, "1-XYZ-789", "Renault", "Clio", Motorisation.ESSENCE));

        Categorie entretien = categories.save(new Categorie("IT-ENT", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-VID", "Vidange", new BigDecimal("49.00"), 60));

        pont1 = postes.save(new PosteAtelier("Pont 1"));

        // Un jour dans une semaine, a 10:00 heure de Bruxelles : dans l horizon, au-dela
        // du delai minimal, et la plage est creee pour ce jour de la semaine.
        LocalDate jour = LocalDate.now(BRUXELLES).plusDays(7);
        lundiProchain10h = jour.atTime(10, 0).atZone(BRUXELLES).toInstant();
        plages.save(new PlageOuverture(jour.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(18, 0)));
    }

    @Test
    @DisplayName("reserve un creneau libre et attribue le premier poste")
    void reserveUnCreneauLibre() {
        Rdv rdv = service.reserver("marie@exemple.be", golf.getReference(),
                List.of(vidange.getReference()), lundiProchain10h, "Bruit au freinage");

        assertThat(rdv.getId()).isNotNull();
        assertThat(rdv.getNumero()).matches("RDV-\\d{4}-\\d{4}");
        assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        assertThat(rdv.getPoste()).isEqualTo(pont1);
        assertThat(rdv.getFin()).isEqualTo(lundiProchain10h.plus(Duration.ofHours(1)));
        assertThat(rdv.getLignes()).hasSize(1);
        assertThat(rdv.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("49.00");
    }

    @Test
    @DisplayName("refuse la seconde demande sur le meme creneau quand le seul poste est pris")
    void refuseLaSecondeDemande() {
        service.reserver("marie@exemple.be", golf.getReference(), List.of(vidange.getReference()), lundiProchain10h, null);

        assertThatThrownBy(() -> service.reserver("paul@exemple.be", clio.getReference(),
                List.of(vidange.getReference()), lundiProchain10h, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("RM-08");
    }

    @Test
    @DisplayName("refuse aussi un chevauchement partiel, tampon compris")
    void refuseLeChevauchementPartiel() {
        service.reserver("marie@exemple.be", golf.getReference(), List.of(vidange.getReference()), lundiProchain10h, null);

        // 11:00 : le precedent finit a 11:00, mais le tampon de 10 min l interdit encore.
        assertThatThrownBy(() -> service.reserver("paul@exemple.be", clio.getReference(),
                List.of(vidange.getReference()), lundiProchain10h.plus(Duration.ofHours(1)), null))
                .isInstanceOf(RegleMetierException.class);

        // 11:30 : libre.
        Rdv suivant = service.reserver("paul@exemple.be", clio.getReference(),
                List.of(vidange.getReference()), lundiProchain10h.plus(Duration.ofMinutes(90)), null);
        assertThat(suivant.getPoste()).isEqualTo(pont1);
    }

    @Test
    @DisplayName("avec deux postes, deux demandes simultanees obtiennent chacune un poste")
    void deuxPostesDeuxDemandes() {
        PosteAtelier pont2 = postes.save(new PosteAtelier("Pont 2"));

        Rdv premier = service.reserver("marie@exemple.be", golf.getReference(), List.of(vidange.getReference()), lundiProchain10h, null);
        Rdv second = service.reserver("paul@exemple.be", clio.getReference(), List.of(vidange.getReference()), lundiProchain10h, null);

        assertThat(premier.getPoste()).isEqualTo(pont1);
        assertThat(second.getPoste()).isEqualTo(pont2);
    }

    @Test
    @DisplayName("la contrainte d exclusion PostgreSQL rejette un chevauchement meme sans passer par le service")
    void laBaseRejetteLeChevauchement() {
        Duration pas = Duration.ofMinutes(30);
        rdvs.saveAndFlush(new Rdv("RDV-IT-0001", marie, golf, pont1, lundiProchain10h, pas, List.of(vidange), null));

        Rdv chevauchant = new Rdv("RDV-IT-0002", paul, clio, pont1,
                lundiProchain10h.plus(Duration.ofMinutes(30)), pas, List.of(vidange), null);

        assertThatThrownBy(() -> rdvs.saveAndFlush(chevauchant))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_rdv_poste_intervalle");
    }

    @Test
    @DisplayName("un rendez-vous annule libere le poste pour la contrainte")
    void unRendezVousAnnuleLibereLePoste() {
        Duration pas = Duration.ofMinutes(30);
        Rdv premier = new Rdv("RDV-IT-0001", marie, golf, pont1, lundiProchain10h, pas, List.of(vidange), null);
        premier.annulerParLeMembre(lundiProchain10h.minus(Duration.ofDays(3)), Duration.ofHours(24));
        rdvs.saveAndFlush(premier);

        Rdv second = rdvs.saveAndFlush(
                new Rdv("RDV-IT-0002", paul, clio, pont1, lundiProchain10h, pas, List.of(vidange), null));

        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("refuse un creneau hors des plages d ouverture")
    void refuseHorsPlage() {
        Instant minuit = lundiProchain10h.atZone(BRUXELLES).withHour(0).toInstant();

        assertThatThrownBy(() -> service.reserver("marie@exemple.be", golf.getReference(),
                List.of(vidange.getReference()), minuit, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("RM-08");
    }

    @Test
    @DisplayName("refuse le vehicule d un autre membre")
    void refuseLeVehiculeDAutrui() {
        assertThatThrownBy(() -> service.reserver("paul@exemple.be", golf.getReference(),
                List.of(vidange.getReference()), lundiProchain10h, null))
                .isInstanceOf(be.autoservplus.common.exception.RessourceIntrouvableException.class);
    }
}