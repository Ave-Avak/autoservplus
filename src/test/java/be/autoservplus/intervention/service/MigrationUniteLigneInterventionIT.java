package be.autoservplus.intervention.service;

import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V73 appliquée à une base qui porte des lignes d'intervention d'avant l'unité.
 *
 * <p>Chaque cas part d'une base neuve amenée à V72 par Flyway, sans {@code db/demo} : c'est
 * le schéma de production, où aucun déclencheur ne complète une ligne écrite sans unité.</p>
 */
@SpringBootTest
@DisplayName("V73 : unité figée et quantité décimale des lignes d'intervention (migration)")
class MigrationUniteLigneInterventionIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private JdbcTemplate jdbc;

    @Value("${spring.datasource.url}") private String urlBase;
    @Value("${spring.datasource.username}") private String identifiant;
    @Value("${spring.datasource.password}") private String secret;

    private JdbcTemplate baseAuStadeV72() {
        String nom = "migration_v73_" + COMPTEUR.getAndIncrement();
        jdbc.execute("CREATE DATABASE " + nom);
        String url = urlBase.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1");
        DriverManagerDataSource source = new DriverManagerDataSource(url, identifiant, secret);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("72").load().migrate();
        return new JdbcTemplate(source);
    }

    private static void appliquerV73(JdbcTemplate base) {
        Flyway.configure().dataSource(base.getDataSource())
                .locations("classpath:db/migration").load().migrate();
    }

    /** Une intervention et ses deux articles : une prestation au forfait et une pièce. */
    private static long intervention(JdbcTemplate base) {
        base.update("INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, prenom, statut, "
                + "email_verifie) VALUES ('MEMBRE', 'marie@exemple.be', 'x', 'Dupont', 'Marie', 'ACTIF', true)");
        long vehicule = base.queryForObject("INSERT INTO vehicule (reference, membre_id, plaque, marque, modele, "
                + "motorisation) SELECT gen_random_uuid(), id, '1-MIG-001', 'VW', 'Golf', 'DIESEL' FROM utilisateur "
                + "WHERE email = 'marie@exemple.be' RETURNING id", Long.class);
        base.update("INSERT INTO categorie (code, libelle, type) VALUES ('MIG-SRV', 'Entretien', 'SERVICE'), "
                + "('MIG-PCE', 'Freinage', 'PIECE')");
        base.update("INSERT INTO service (reference, categorie_id, code, libelle, prix_htva, duree_minutes) "
                + "SELECT gen_random_uuid(), id, 'MIG-VID', 'Vidange', 49.00, 30 FROM categorie WHERE code = 'MIG-SRV'");
        base.update("INSERT INTO piece (reference, categorie_id, reference_fabricant, libelle, prix_htva) "
                + "SELECT gen_random_uuid(), id, 'MIG-PL', 'Plaquettes', 40.00 FROM categorie WHERE code = 'MIG-PCE'");
        return base.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva) "
                + "VALUES ('INT-MIG-0001', ?, 'PLANIFIEE', 49.00) RETURNING id", Long.class, vehicule);
    }

    private static Map<String, Object> ligne(JdbcTemplate base, String libelle) {
        return base.queryForMap("SELECT unite, quantite FROM ligne_intervention WHERE libelle_fige = ?", libelle);
    }

    @Test
    @DisplayName("une ligne de pièce passe en PIECE, une ligne de prestation en FORFAIT, quantités intactes")
    void repriseSansPerte() {
        JdbcTemplate base = baseAuStadeV72();
        long intervention = intervention(base);
        base.update("INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite, "
                + "prix_unitaire_htva, taux_tva) SELECT ?, id, 'Vidange', 2, 49.00, 21.00 FROM service "
                + "WHERE code = 'MIG-VID'", intervention);
        base.update("INSERT INTO ligne_intervention (intervention_id, piece_id, libelle_fige, quantite, "
                + "prix_unitaire_htva, taux_tva, ajoutee_en_cours, accord_membre) SELECT ?, id, 'Plaquettes', 3, "
                + "40.00, 21.00, true, true FROM piece WHERE reference_fabricant = 'MIG-PL'", intervention);

        appliquerV73(base);

        assertThat(ligne(base, "Vidange").get("unite")).isEqualTo("FORFAIT");
        assertThat((BigDecimal) ligne(base, "Vidange").get("quantite")).isEqualByComparingTo("2");
        assertThat(ligne(base, "Plaquettes").get("unite")).isEqualTo("PIECE");
        assertThat((BigDecimal) ligne(base, "Plaquettes").get("quantite")).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("schéma de production : une ligne sans unité est refusée, une pièce déclarée au forfait aussi")
    void insertionSansUniteRefusee() {
        JdbcTemplate base = baseAuStadeV72();
        long intervention = intervention(base);
        appliquerV73(base);

        assertThatThrownBy(() -> base.update("INSERT INTO ligne_intervention (intervention_id, service_id, "
                + "libelle_fige, quantite, prix_unitaire_htva, taux_tva) SELECT ?, id, 'Vidange', 1, 49.00, 21.00 "
                + "FROM service WHERE code = 'MIG-VID'", intervention))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("unite");
        assertThatThrownBy(() -> base.update("INSERT INTO ligne_intervention (intervention_id, piece_id, "
                + "libelle_fige, quantite, prix_unitaire_htva, taux_tva, unite, ajoutee_en_cours, accord_membre) "
                + "SELECT ?, id, 'Plaquettes', 1, 40.00, 21.00, 'FORFAIT', true, true FROM piece "
                + "WHERE reference_fabricant = 'MIG-PL'", intervention))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ligne_interv_unite");
    }

    @Test
    @DisplayName("schéma de production : une quantité décimale n'est admise que pour des heures")
    void quantiteDecimaleReserveeAuxHeures() {
        JdbcTemplate base = baseAuStadeV72();
        long intervention = intervention(base);
        appliquerV73(base);

        assertThatThrownBy(() -> base.update("INSERT INTO ligne_intervention (intervention_id, service_id, "
                + "libelle_fige, quantite, prix_unitaire_htva, taux_tva, unite) SELECT ?, id, 'Vidange', 1.5, "
                + "49.00, 21.00, 'FORFAIT' FROM service WHERE code = 'MIG-VID'", intervention))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ligne_interv_quantite_unite");
        base.update("INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite, "
                + "prix_unitaire_htva, taux_tva, unite) SELECT ?, id, 'Main-d''œuvre', 1.5, 65.00, 21.00, 'HEURE' "
                + "FROM service WHERE code = 'MIG-VID'", intervention);
        assertThat((BigDecimal) ligne(base, "Main-d'œuvre").get("quantite")).isEqualByComparingTo("1.5");
    }
}
