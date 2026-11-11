package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
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

    @Test
    @DisplayName("base migrée avec les graines : une réservation passe du premier coup")
    void reservationApresGraine() {
        Flyway.configure().dataSource(source)
                .locations("classpath:db/migration", "classpath:db/demo")
                .load().migrate();
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
