package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.messagerie.service.MessagerieService;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.web.dto.CreneauVue;
import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une base de démonstration porte des rendez-vous numérotés par la graine. La première
 * réservation faite par l'application doit recevoir un numéro libre, du premier coup.
 *
 * <p>Les graines de {@code db/demo} ne sont jouées par aucun démarrage de test. Elles
 * sont appliquées ici par Flyway, comme sous le profil {@code demo}, sur la base propre
 * à cette classe : un script lu par {@code ResourceDatabasePopulator} ne saurait pas
 * découper les blocs {@code DO $$} de V900.</p>
 */
@SpringBootTest
@DisplayName("Numérotation des rendez-vous après la graine de démonstration (intégration)")
class NumerotationApresGraineIT extends SocleIntegration {

    private static final String MARIE = "marie.dupont@demo.test";

    @Autowired private DataSource source;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RdvService reservations;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private PrestationRepository prestations;
    @Autowired private MessagerieService messagerie;

    private void migrerAvecLesGraines() {
        Flyway.configure().dataSource(source)
                .locations("classpath:db/migration", "classpath:db/demo")
                .load().migrate();
    }

    @Test
    @DisplayName("base migrée avec les graines : une réservation passe du premier coup")
    void reservationApresGraine() {
        migrerAvecLesGraines();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rdv", Long.class))
                .as("la graine porte des rendez-vous").isPositive();

        Vehicule vehicule = vehicules.findByMembre(MARIE).get(0);
        Prestation prestation = prestations.findByActifTrueOrderByLibelleAsc().get(0);
        List<UUID> choix = List.of(prestation.getReference());
        Instant debut = premierCreneauLibre(choix);

        Rdv rdv = reservations.reserver(MARIE, vehicule.getReference(), choix, debut, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM rdv WHERE numero = ?", Long.class, rdv.getNumero()))
                .isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = MARIE)
    @DisplayName("base migrée avec les graines : le fil de la graine est numéroté, le suivant sans collision")
    void filApresGraine() {
        migrerAvecLesGraines();
        List<String> numerosDeLaGraine = jdbc.queryForList(
                "SELECT numero FROM conversation WHERE created_by = 'demo'", String.class);
        assertThat(numerosDeLaGraine).isNotEmpty().allMatch(numero -> numero.matches("FIL-\\d{4}-\\d{4}"));

        UUID fil = messagerie.ouvrir(MARIE, "Après la graine", "Bonjour, une question.", null);

        String numero = jdbc.queryForObject("SELECT numero FROM conversation WHERE reference = ?",
                String.class, fil);
        assertThat(numero).isNotIn(numerosDeLaGraine);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation WHERE numero = ?", Long.class, numero))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("base migrée avec les graines : les lignes de pièce de la graine sont en PIECE, les autres au forfait")
    void unitesDesLignesDeLaGraine() {
        migrerAvecLesGraines();

        // V900 insère huit lignes : trois pièces (filtre, batterie, plaquettes) et cinq
        // prestations (révision, vidange, deux plaquettes, diagnostic de l'intervention annulée).

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ligne_intervention WHERE created_by = 'demo' "
                + "AND piece_id IS NOT NULL AND unite = 'PIECE'", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ligne_intervention WHERE created_by = 'demo' "
                + "AND piece_id IS NULL AND unite = 'FORFAIT'", Long.class)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ligne_intervention WHERE created_by = 'demo'",
                Long.class)).isEqualTo(8L);
    }

    @Test
    @DisplayName("base migrée avec les graines : la main-d'œuvre de démonstration est à l'heure, 65,00 HTVA, une heure")
    void prestationHoraireDeDemonstration() {
        migrerAvecLesGraines();

        var prestation = jdbc.queryForMap("SELECT unite, prix_htva, duree_minutes, actif FROM service "
                + "WHERE code = 'MAIN_OEUVRE_HEURE'");
        assertThat(prestation.get("unite")).isEqualTo("HEURE");
        assertThat((java.math.BigDecimal) prestation.get("prix_htva")).isEqualByComparingTo("65.00");
        assertThat(prestation.get("duree_minutes")).isEqualTo(60);
        assertThat(prestation.get("actif")).isEqualTo(true);
    }

    private Instant premierCreneauLibre(List<UUID> choix) {
        LocalDate jour = reservations.premierJourReservable().plusDays(1);
        LocalDate dernier = reservations.dernierJourReservable();
        for (; !jour.isAfter(dernier); jour = jour.plusDays(1)) {
            Optional<CreneauVue> creneau = reservations.creneauxPour(jour, choix).stream().findFirst();
            if (creneau.isPresent()) {
                return Instant.parse(creneau.get().valeur());
            }
        }
        throw new IllegalStateException("Aucun créneau libre sur l'horizon de réservation.");
    }
}
