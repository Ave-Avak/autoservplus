package be.autoservplus.infrastructure;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private UtilisateurRepository utilisateurs;

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
    @DisplayName("le seed catalogue insere au moins une prestation par categorie SERVICE")
    void leSeedCatalogueEstApplique() {
        // V16 insere le catalogue de demo (9 prestations sur 6 categories SERVICE).
        // Sans ce test, la suppression accidentelle de V16 ou une regression de FK
        // resterait invisible jusqu au premier essai de reservation.
        Integer nombreServices = jdbc.queryForObject(
                "SELECT count(*) FROM service WHERE actif = TRUE", Integer.class);
        assertThat(nombreServices)
                .as("Le seed doit inserer au moins 6 prestations actives (une par categorie SERVICE)")
                .isGreaterThanOrEqualTo(6);

        Integer categoriesCouvertes = jdbc.queryForObject(
                "SELECT count(DISTINCT s.categorie_id) FROM service s WHERE s.actif = TRUE", Integer.class);
        assertThat(categoriesCouvertes)
                .as("Chaque categorie SERVICE doit avoir au moins une prestation")
                .isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("le compte admin de seed est connectable avec le mot de passe documente")
    void leCompteAdminDeSeedEstConnectable() {
        // V10 insere l admin, V15 corrige son hash. Sans le test, un desaccord entre
        // le hash et le mot de passe documente (comme c etait le cas avant V15) reste
        // invisible jusqu au premier essai de connexion.
        Utilisateur admin = utilisateurs.findByEmailIgnoreCase("admin@autoservplus.be")
                .orElseThrow(() -> new AssertionError(
                        "Le seed V10 doit inserer un compte admin@autoservplus.be"));

        assertThat(new BCryptPasswordEncoder(12).matches("ChangezMoi2026!", admin.getMotDePasseHache()))
                .as("Le hash BCrypt du seed doit correspondre au mot de passe documente \"ChangezMoi2026!\"")
                .isTrue();
    }

    @Test
    @DisplayName("ne contient plus la table des creneaux generes")
    void supprimeLesCreneaux() {
        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'creneau_horaire'",
                Integer.class);
        assertThat(nombre).isZero();
    }

    // Les trois tests suivants operent sur jour_semaine = 7 (dimanche), jamais peuple
    // par le seed V10. @Transactional garantit que les INSERT sont rollback en fin de
    // test, ce qui evite d avoir a nettoyer manuellement et preserve l isolation.

    @Test
    @Transactional
    @DisplayName("rejette deux plages d ouverture chevauchantes le meme jour")
    void rejetteChevauchementPlageOuverture() {
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '08:00', '12:00')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '09:00', '11:00')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_plage_ouverture_chevauchement");
    }

    @Test
    @Transactional
    @DisplayName("accepte deux plages adjacentes le meme jour grace a la borne demi-ouverte")
    void accepteAdjacencesPlageOuverture() {
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '12:00', '13:00')");
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '13:00', '17:00')");

        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM plage_ouverture WHERE jour_semaine = 7", Integer.class);
        assertThat(nombre).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("ignore les plages soft-deletees dans la contrainte d exclusion")
    void ignoreSoftDeleteePlageOuverture() {
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin, deleted_at) " +
                "VALUES (7, '08:00', '12:00', now())");
        // Une plage active peut occuper l intervalle libere par la suppression logique.
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '09:00', '11:00')");

        Integer actives = jdbc.queryForObject(
                "SELECT count(*) FROM plage_ouverture WHERE jour_semaine = 7 AND deleted_at IS NULL",
                Integer.class);
        assertThat(actives).isEqualTo(1);
    }
}