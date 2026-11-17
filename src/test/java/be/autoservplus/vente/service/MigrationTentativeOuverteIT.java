package be.autoservplus.vente.service;

import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V51 appliquée à une base qui porte des tentatives de paiement d'avant le changement.
 *
 * <p>Chaque cas part d'une base neuve amenée à V50 par Flyway, y écrit des commandes et
 * des tentatives telles que l'ancien code les laissait, puis applique V51.</p>
 */
@SpringBootTest
@DisplayName("V51 : une seule tentative ouverte par commande (migration)")
class MigrationTentativeOuverteIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private JdbcTemplate jdbc;

    @Value("${spring.datasource.url}") private String urlBase;
    @Value("${spring.datasource.username}") private String identifiant;
    @Value("${spring.datasource.password}") private String secret;

    private JdbcTemplate baseAuStadeV50() {
        String nom = "migration_v51_" + COMPTEUR.getAndIncrement();
        jdbc.execute("CREATE DATABASE " + nom);
        String url = urlBase.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1");
        DriverManagerDataSource source = new DriverManagerDataSource(url, identifiant, secret);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("50").load().migrate();
        JdbcTemplate base = new JdbcTemplate(source);
        base.update("INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, "
                + "prenom, statut, email_verifie) VALUES ('MEMBRE', 'marie@exemple.be', 'x', "
                + "'Dupont', 'Marie', 'ACTIF', true)");
        return base;
    }

    private static void appliquerV51(JdbcTemplate base) {
        Flyway.configure().dataSource(base.getDataSource())
                .locations("classpath:db/migration").load().migrate();
    }

    private static long commande(JdbcTemplate base, String numero, String statut) {
        return base.queryForObject("INSERT INTO commande (numero, membre_id, statut, montant_htva, "
                + "montant_tva, montant_tvac) SELECT ?, id, ?, 10.00, 2.10, 12.10 FROM utilisateur "
                + "WHERE email = 'marie@exemple.be' RETURNING id", Long.class, numero, statut);
    }

    private static void tentative(JdbcTemplate base, long commande, String reference, String statut) {
        base.update("INSERT INTO paiement (commande_id, reference_mollie, cle_idempotence, montant, "
                + "statut) VALUES (?, ?, gen_random_uuid()::text, 12.10, ?)", commande, reference, statut);
    }

    private static Map<String, Object> ligne(JdbcTemplate base, String reference) {
        return base.queryForMap("SELECT statut, updated_by, date_finalisation, version FROM paiement "
                + "WHERE reference_mollie = ?", reference);
    }

    @Test
    @DisplayName("tentatives ouvertes d'une commande payée : closes, signées de la migration")
    void tentativesDUneCommandePayeeCloses() {
        JdbcTemplate base = baseAuStadeV50();
        long payee = commande(base, "CMD-2026-0001", "PAYEE");
        tentative(base, payee, "tr_ancienne_1", "INITIE");
        tentative(base, payee, "tr_ancienne_2", "INITIE");
        tentative(base, payee, "tr_payee", "REUSSI");
        long enAttente = commande(base, "CMD-2026-0002", "EN_ATTENTE_PAIEMENT");
        tentative(base, enAttente, "tr_en_attente", "INITIE");

        appliquerV51(base);

        for (String reference : List.of("tr_ancienne_1", "tr_ancienne_2")) {
            Map<String, Object> close = ligne(base, reference);
            assertThat(close.get("statut")).isEqualTo("EXPIRE");
            assertThat(close.get("updated_by")).isEqualTo("migration V51");
            assertThat(close.get("date_finalisation")).isNotNull();
            assertThat(close.get("version")).isEqualTo(1L);
        }
        assertThat(ligne(base, "tr_payee").get("statut")).isEqualTo("REUSSI");
        // La seule tentative d'une commande qui attend encore son paiement est intacte.
        assertThat(ligne(base, "tr_en_attente").get("statut")).isEqualTo("INITIE");
        assertThat(ligne(base, "tr_en_attente").get("updated_by")).isNull();
    }

    @Test
    @DisplayName("deux tentatives ouvertes sur une commande en attente : migration refusée, commande nommée")
    void commandeEnAttenteAvecDeuxTentatives() {
        JdbcTemplate base = baseAuStadeV50();
        long enAttente = commande(base, "CMD-2026-0003", "EN_ATTENTE_PAIEMENT");
        tentative(base, enAttente, "tr_premiere", "INITIE");
        tentative(base, enAttente, "tr_seconde", "EN_COURS");

        assertThatThrownBy(() -> appliquerV51(base))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("CMD-2026-0003 (2 tentatives ouvertes)");
        // Rien n'est clos à l'aveugle.
        assertThat(ligne(base, "tr_premiere").get("statut")).isEqualTo("INITIE");
        assertThat(ligne(base, "tr_seconde").get("statut")).isEqualTo("EN_COURS");
    }

    @Test
    @DisplayName("après V51, la base refuse une seconde tentative ouverte pour la même commande")
    void indexRefuseUneSecondeTentativeOuverte() {
        JdbcTemplate base = baseAuStadeV50();
        long enAttente = commande(base, "CMD-2026-0004", "EN_ATTENTE_PAIEMENT");
        tentative(base, enAttente, "tr_ouverte", "INITIE");
        appliquerV51(base);

        assertThatThrownBy(() -> tentative(base, enAttente, "tr_doublon", "EN_COURS"))
                .hasMessageContaining("uq_paiement_tentative_ouverte");
        // Une tentative close ne compte pas.
        tentative(base, enAttente, "tr_close", "ECHOUE");
    }
}
