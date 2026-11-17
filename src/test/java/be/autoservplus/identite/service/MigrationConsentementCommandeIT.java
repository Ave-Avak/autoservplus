package be.autoservplus.identite.service;

import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V52 appliquée à une base qui porte des consentements d'avant le rattachement.
 *
 * <p>La base est amenée à V51 par Flyway, reçoit des commandes et des consentements
 * tels que l'ancien code les écrivait, l'acceptation quelques millisecondes après la
 * commande, puis V52 s'applique.</p>
 */
@SpringBootTest
@DisplayName("V52 : consentements rattachés à leur commande (migration)")
class MigrationConsentementCommandeIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final Instant MIDI = Instant.parse("2026-10-27T11:00:00Z");

    @Autowired private JdbcTemplate jdbc;

    @Value("${spring.datasource.url}") private String urlBase;
    @Value("${spring.datasource.username}") private String identifiant;
    @Value("${spring.datasource.password}") private String secret;

    private JdbcTemplate baseAuStadeV51() {
        String nom = "migration_v52_" + COMPTEUR.getAndIncrement();
        jdbc.execute("CREATE DATABASE " + nom);
        String url = urlBase.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1");
        DriverManagerDataSource source = new DriverManagerDataSource(url, identifiant, secret);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("51").load().migrate();
        JdbcTemplate base = new JdbcTemplate(source);
        base.update("INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, "
                + "prenom, statut, email_verifie) VALUES ('MEMBRE', 'marie@exemple.be', 'x', "
                + "'Dupont', 'Marie', 'ACTIF', true)");
        return base;
    }

    private static long commande(JdbcTemplate base, String numero, Instant date) {
        return base.queryForObject("INSERT INTO commande (numero, membre_id, statut, montant_htva, "
                + "montant_tva, montant_tvac, date_commande) SELECT ?, id, 'PAYEE', 10.00, 2.10, "
                + "12.10, ? FROM utilisateur WHERE email = 'marie@exemple.be' RETURNING id",
                Long.class, numero, Timestamp.from(date));
    }

    private static long consentement(JdbcTemplate base, String type, Instant date) {
        return base.queryForObject("INSERT INTO consentement (utilisateur_id, type_document, "
                + "version_acceptee, accorde, date_consentement) SELECT id, ?, 'V-1', true, ? "
                + "FROM utilisateur WHERE email = 'marie@exemple.be' RETURNING id",
                Long.class, type, Timestamp.from(date));
    }

    private static Map<String, Object> ligne(JdbcTemplate base, long id) {
        return base.queryForMap("SELECT commande_id, updated_by FROM consentement WHERE id = ?", id);
    }

    @Test
    @DisplayName("une seule commande répond : reliée. Plusieurs ou aucune : laissée sans commande")
    void rapprochement() {
        JdbcTemplate base = baseAuStadeV51();
        long seule = commande(base, "CMD-2026-0001", MIDI);
        long cgv = consentement(base, "CGV", MIDI.plusMillis(30));
        long renonciation = consentement(base, "RENONCIATION_RETRACTATION", MIDI.plusMillis(40));
        // Deux commandes à une seconde d'écart : la preuve ne sait pas laquelle.
        commande(base, "CMD-2026-0002", MIDI.plusSeconds(3600));
        commande(base, "CMD-2026-0003", MIDI.plusSeconds(3601));
        long ambigue = consentement(base, "CGV", MIDI.plusSeconds(3601).plusMillis(500));
        long orpheline = consentement(base, "CGV", MIDI.minusSeconds(86400));
        long cookies = consentement(base, "COOKIES_ANALYTIQUE", MIDI.plusMillis(50));

        Flyway.configure().dataSource(base.getDataSource())
                .locations("classpath:db/migration").load().migrate();

        assertThat(ligne(base, cgv).get("commande_id")).isEqualTo(seule);
        assertThat(ligne(base, cgv).get("updated_by")).isEqualTo("migration V52");
        assertThat(ligne(base, renonciation).get("commande_id")).isEqualTo(seule);
        assertThat(ligne(base, ambigue).get("commande_id")).isNull();
        assertThat(ligne(base, orpheline).get("commande_id")).isNull();
        assertThat(ligne(base, cookies).get("commande_id")).isNull();
        assertThat(ligne(base, cookies).get("updated_by")).isNull();
    }
}
