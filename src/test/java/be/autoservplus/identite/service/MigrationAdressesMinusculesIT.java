package be.autoservplus.identite.service;

import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V55 appliquée à une base qui porte des adresses écrites avant la contrainte, puis V56
 * qui porte l'index de l'adresse en attente sur la colonne elle-même.
 *
 * <p>Chaque cas part d'une base neuve amenée à V54 par Flyway, y écrit des comptes tels
 * qu'un ancien chemin d'écriture aurait pu les laisser, puis applique les migrations
 * suivantes.</p>
 */
@SpringBootTest
@DisplayName("V55 et V56 : adresses de courriel en minuscules (migration)")
class MigrationAdressesMinusculesIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private JdbcTemplate jdbc;

    @Value("${spring.datasource.url}") private String urlBase;
    @Value("${spring.datasource.username}") private String identifiant;
    @Value("${spring.datasource.password}") private String secret;

    private JdbcTemplate baseAuStadeV54() {
        String nom = "migration_v55_" + COMPTEUR.getAndIncrement();
        jdbc.execute("CREATE DATABASE " + nom);
        String url = urlBase.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1");
        DriverManagerDataSource source = new DriverManagerDataSource(url, identifiant, secret);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("54").load().migrate();
        return new JdbcTemplate(source);
    }

    private static long compte(JdbcTemplate base, String email, String enAttente) {
        // Une demande en attente porte son jeton et son échéance
        // (ck_utilisateur_changement_email_complet).
        return base.queryForObject("INSERT INTO utilisateur (type_utilisateur, email, "
                + "mot_de_passe_hache, nom, prenom, statut, email_verifie, email_en_attente, "
                + "jeton_changement_email_hache, jeton_changement_expiration) "
                + "VALUES ('MEMBRE', ?, 'x', 'Nom', 'Prenom', 'ACTIF', true, ?, "
                + "CASE WHEN ?::text IS NULL THEN NULL ELSE md5(random()::text) || md5(random()::text) END, "
                + "CASE WHEN ?::text IS NULL THEN NULL ELSE now() + interval '1 day' END) RETURNING id",
                Long.class, email, enAttente, enAttente, enAttente);
    }

    private static void appliquerV55(JdbcTemplate base) {
        Flyway.configure().dataSource(base.getDataSource())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    @DisplayName("adresses en majuscules mises en minuscules, puis la base refuse toute majuscule")
    void miseEnMinusculesPuisContrainte() {
        JdbcTemplate base = baseAuStadeV54();
        long marie = compte(base, "Marie@Exemple.BE", "Nouvelle@Exemple.BE");
        compte(base, "paul@exemple.be", null);

        appliquerV55(base);

        assertThat(base.queryForMap("SELECT email, email_en_attente FROM utilisateur WHERE id = ?", marie))
                .containsEntry("email", "marie@exemple.be")
                .containsEntry("email_en_attente", "nouvelle@exemple.be");
        assertThatThrownBy(() -> compte(base, "Jean@exemple.be", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_utilisateur_email_minuscules");
        assertThatThrownBy(() -> compte(base, "jean@exemple.be", "Autre@exemple.be"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_utilisateur_email_en_attente_minuscules");
    }

    @Test
    @DisplayName("deux adresses qui ne diffèrent que par la casse : migration refusée, comptes nommés")
    void doublonDeCasse() {
        JdbcTemplate base = baseAuStadeV54();
        long premier = compte(base, "marie@exemple.be", null);
        long second = compte(base, "Marie@exemple.be", null);

        assertThatThrownBy(() -> appliquerV55(base))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V55")
                .hasMessageContaining(premier + ", " + second);
        assertThat(base.queryForObject("SELECT count(*) FROM utilisateur WHERE email = 'Marie@exemple.be'",
                Integer.class)).as("rien n'est modifié").isEqualTo(1);
    }

    @Test
    @DisplayName("deux demandes en attente ne peuvent pas ne différer que par la casse, dès V38")
    void doublonDeCasseEnAttenteImpossible() {
        JdbcTemplate base = baseAuStadeV54();
        compte(base, "a@exemple.be", "cible@exemple.be");

        assertThatThrownBy(() -> compte(base, "b@exemple.be", "Cible@exemple.be"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_utilisateur_email_en_attente");
    }

    @Test
    @DisplayName("V56 : la recherche stricte de l'adresse en attente passe par son index d'unicité")
    void indexAdresseEnAttente() {
        JdbcTemplate base = baseAuStadeV54();
        appliquerV55(base);
        compte(base, "a@exemple.be", "cible@exemple.be");

        assertThatThrownBy(() -> compte(base, "b@exemple.be", "cible@exemple.be"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_utilisateur_email_en_attente");
        // Parcours séquentiel interdit à la session, pour qu'une table presque vide ne le
        // préfère pas. Sur l'index de V38, le plan parcourt l'index partiel en entier et
        // n'applique l'adresse qu'en filtre : seule une condition d'index prouve qu'il sert.
        String plan = base.execute((ConnectionCallback<String>) connexion -> {
            try (Statement requete = connexion.createStatement()) {
                requete.execute("SET enable_seqscan = off");
                StringBuilder lignes = new StringBuilder();
                try (ResultSet resultat = requete.executeQuery("EXPLAIN SELECT id FROM utilisateur "
                        + "WHERE email_en_attente = 'cible@exemple.be'")) {
                    while (resultat.next()) {
                        lignes.append(resultat.getString(1)).append(System.lineSeparator());
                    }
                }
                return lignes.toString();
            }
        });
        assertThat(plan).contains("uq_utilisateur_email_en_attente").contains("Index Cond");
    }
}
