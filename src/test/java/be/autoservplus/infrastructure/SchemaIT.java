package be.autoservplus.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejoue l ensemble des migrations sur un PostgreSQL neuf et verifie les elements de
 * schema que les tests unitaires ne peuvent pas voir.
 *
 * <p>Ce test est le seul a garantir que la base de production pourra etre construite
 * a partir de zero : extension, contraintes, donnees structurelles. Il tourne sous
 * Failsafe (suffixe IT) et requiert Docker.</p>
 */
@SpringBootTest
@Testcontainers
@DisplayName("Schema de base de donnees")
class SchemaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("installe l extension btree_gist requise par la contrainte d exclusion")
    void installeBtreeGist() {
        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(nombre).isEqualTo(1);
    }

    @Test
    @DisplayName("cree la ligne unique de parametres d atelier")
    void creeLesParametres() {
        Integer nombre = jdbc.queryForObject("SELECT count(*) FROM parametre_atelier", Integer.class);
        assertThat(nombre).isEqualTo(1);
    }

    @Test
    @DisplayName("pose la contrainte d exclusion sur les rendez-vous")
    void poseLaContrainteDExclusion() {
        String type = jdbc.queryForObject(
                "SELECT contype FROM pg_constraint WHERE conname = 'ex_rdv_poste_intervalle'", String.class);
        assertThat(type).isEqualTo("x");
    }

    @Test
    @DisplayName("ne contient plus la table des creneaux generes")
    void supprimeLesCreneaux() {
        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'creneau_horaire'",
                Integer.class);
        assertThat(nombre).isZero();
    }
}