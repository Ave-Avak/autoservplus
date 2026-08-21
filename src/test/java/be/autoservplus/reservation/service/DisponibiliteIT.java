package be.autoservplus.reservation.service;

import be.autoservplus.reservation.service.dto.CreneauDisponible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prouve que la chaine complete de disponibilite fonctionne apres application
 * des migrations : plages d ouverture (V10), postes seedes (V17), catalogue (V16)
 * et parametres atelier (V13) s enchainent pour proposer des creneaux reels sur
 * un jour ouvre.
 *
 * <p>C est le garde-fou qui aurait detecte l absence de seed poste : le check
 * de presence dans SchemaIT localise le trou (poste manquant), ce test-ci prouve
 * que la reservation est effectivement possible bout en bout.</p>
 *
 * <p>Horloge figee sur un vendredi connu ; le lundi suivant est vise pour la
 * requete (jour ouvre, +3 jours = au-dela du delai minimal de 24 h, largement
 * dans l horizon de 60 j). Le choix est independant du jour d execution du
 * test — cf. le meme pattern que RdvServiceIT.</p>
 */
@SpringBootTest
@Testcontainers
@Import(DisponibiliteIT.HorlogeFixe.class)
@DisplayName("Disponibilite (integration)")
class DisponibiliteIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    // Vendredi 11 septembre 2026 a 09:00 Bruxelles. Le lundi suivant (14 sept)
    // est un jour ouvre, +3 j, dans la fenetre [+24h, +60j] par tous les
    // parametres par defaut de l atelier.
    private static final Instant INSTANT_FIGE =
            LocalDate.of(2026, 9, 11).atTime(9, 0).atZone(BRUXELLES).toInstant();
    private static final LocalDate LUNDI_OUVRE = LocalDate.of(2026, 9, 14);

    @TestConfiguration
    static class HorlogeFixe {
        @Bean
        @Primary
        Clock horlogeFigee() {
            return Clock.fixed(INSTANT_FIGE, BRUXELLES);
        }
    }

    @Autowired private DisponibiliteService disponibilites;

    @Test
    @DisplayName("des creneaux sont proposes un jour ouvre apres application du seed")
    void desCreneauxSontProposesUnJourOuvre() {
        List<CreneauDisponible> creneaux = disponibilites.creneauxDuJour(LUNDI_OUVRE, 30);

        assertThat(creneaux)
                .as("Sans creneaux, la reservation est impossible : soit le seed poste manque, "
                        + "soit une plage d ouverture est absente, soit la fenetre temporelle est mal calculee")
                .isNotEmpty();
    }
}
