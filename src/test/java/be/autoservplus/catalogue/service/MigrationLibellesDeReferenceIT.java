package be.autoservplus.catalogue.service;

import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V67 et la graine V905 : les libellés écrits sans accents par V10, V16 et V900 sont
 * corrigés et traduits, sans jamais toucher une ligne que le garage a reprise.
 *
 * <p>Chaque cas part d'une base neuve, amenée par Flyway au stade voulu : la base
 * partagée des tests est déjà migrée, et d'autres classes y écrivent leur catalogue.</p>
 */
@SpringBootTest
@DisplayName("V67 et V905 : libellés de référence accentués et traduits (migration)")
class MigrationLibellesDeReferenceIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    /** Tout texte de graine écrit sans accents que les deux migrations doivent avoir fait disparaître. */
    private static final String SANS_ACCENTS = "(Revision|arriere|equilibrage|electronique|electricite|"
            + "controle|Pieces de|Filtre a |reparations|etriers|synthetique|demarrage|elevat)";

    @Autowired private JdbcTemplate jdbc;

    @Value("${spring.datasource.url}") private String urlBase;
    @Value("${spring.datasource.username}") private String identifiant;
    @Value("${spring.datasource.password}") private String secret;

    private DriverManagerDataSource baseNeuve() {
        String nom = "migration_v67_" + COMPTEUR.getAndIncrement();
        jdbc.execute("CREATE DATABASE " + nom);
        String url = urlBase.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1");
        return new DriverManagerDataSource(url, identifiant, secret);
    }

    private static List<String> colonne(JdbcTemplate base, String requete, Object... valeurs) {
        return base.queryForList(requete, String.class, valeurs);
    }

    @Test
    @DisplayName("base neuve : prestations, catégories et administrateur accentués, catalogue traduit")
    void baseNeuve_catalogueCorrige() {
        DriverManagerDataSource source = baseNeuve();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JdbcTemplate base = new JdbcTemplate(source);

        assertThat(colonne(base, "SELECT libelle FROM service ORDER BY code"))
                .containsExactly("Diagnostic électronique", "Montage et équilibrage (1 pneu)",
                        "Permutation des 4 pneus", "Remplacement plaquettes arrière",
                        "Remplacement plaquettes avant", "Préparation contrôle technique",
                        "Retouche peinture", "Révision annuelle", "Vidange standard");
        assertThat(colonne(base, "SELECT libelle FROM service WHERE libelle_nl IS NULL OR libelle_en IS NULL"))
                .as("prestations sans traduction").isEmpty();
        assertThat(colonne(base, "SELECT libelle FROM categorie WHERE libelle_nl IS NULL OR libelle_en IS NULL"))
                .as("catégories sans traduction").isEmpty();
        assertThat(colonne(base, "SELECT libelle_nl || ' / ' || libelle_en FROM service WHERE code = 'REVISION_ANNUELLE'"))
                .containsExactly("Jaarlijks onderhoud / Annual service");
        assertThat(colonne(base, "SELECT libelle || ' / ' || libelle_nl FROM categorie WHERE code = 'P_BATTERIE'"))
                .containsExactly("Batteries et électricité / Batterijen en elektriciteit");
        assertThat(colonne(base, "SELECT libelle FROM categorie WHERE libelle ~ ? OR description ~ ? "
                + "UNION ALL SELECT libelle FROM service WHERE libelle ~ ?", SANS_ACCENTS, SANS_ACCENTS, SANS_ACCENTS))
                .as("textes de graine restés sans accents").isEmpty();
        assertThat(colonne(base, "SELECT prenom || ' ' || nom || ', ' || fonction FROM utilisateur "
                + "WHERE email = 'admin@autoservplus.be'"))
                .containsExactly("Système Administrateur, Gérant");
        assertThat(colonne(base, "SELECT libelle_nl FROM categorie WHERE code = 'ENTRETIEN'"))
                .containsExactly("Periodiek onderhoud");
        assertThat(colonne(base, "SELECT description FROM poste_atelier ORDER BY ordre"))
                .containsExactly("Pont élévateur principal", "Pont élévateur secondaire",
                        "Baie sans élévation pour diagnostic et pneumatique");
    }

    @Test
    @DisplayName("une ligne reprise par le garage est laissée telle quelle : libellé, traduction, nom du compte")
    void lignesReprisesIntactes() {
        DriverManagerDataSource source = baseNeuve();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("66").load().migrate();
        JdbcTemplate base = new JdbcTemplate(source);
        base.update("UPDATE service SET libelle = 'Entretien complet' WHERE code = 'REVISION_ANNUELLE'");
        base.update("UPDATE service SET libelle_nl = 'Olie verversen' WHERE code = 'VIDANGE_STD'");
        base.update("UPDATE categorie SET libelle = 'Électricité' WHERE code = 'P_BATTERIE'");
        // Le garage a déjà créé sa propre « Diagnostic électronique » : la prestation de
        // la graine ne peut pas prendre le même nom (A1).
        base.update("INSERT INTO service (categorie_id, code, libelle, prix_htva, duree_minutes) "
                + "SELECT id, 'DIAG_MAISON', 'Diagnostic électronique', 60.00, 30 FROM categorie WHERE code = 'DIAGNOSTIC'");
        base.update("UPDATE utilisateur SET prenom = 'Karim', nom = 'Haddad', fonction = 'Chef d''atelier' "
                + "WHERE email = 'admin@autoservplus.be'");
        base.update("UPDATE poste_atelier SET description = 'Pont deux colonnes' WHERE libelle = 'Pont 1'");

        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();

        assertThat(base.queryForMap("SELECT libelle, libelle_nl, libelle_en FROM service WHERE code = 'REVISION_ANNUELLE'"))
                .containsEntry("libelle", "Entretien complet")
                .containsEntry("libelle_nl", null).containsEntry("libelle_en", null);
        assertThat(base.queryForMap("SELECT libelle_nl, libelle_en FROM service WHERE code = 'VIDANGE_STD'"))
                .as("une traduction saisie n'est ni remplacée ni complétée")
                .containsEntry("libelle_nl", "Olie verversen").containsEntry("libelle_en", null);
        assertThat(base.queryForMap("SELECT libelle, libelle_nl FROM categorie WHERE code = 'P_BATTERIE'"))
                .containsEntry("libelle", "Électricité").containsEntry("libelle_nl", null);
        assertThat(base.queryForMap("SELECT libelle, libelle_nl FROM service WHERE code = 'DIAG_ELEC'"))
                .containsEntry("libelle", "Diagnostic electronique").containsEntry("libelle_nl", null);
        assertThat(colonne(base, "SELECT prenom || ' ' || nom FROM utilisateur WHERE email = 'admin@autoservplus.be'"))
                .containsExactly("Karim Haddad");
        assertThat(colonne(base, "SELECT fonction FROM utilisateur WHERE email = 'admin@autoservplus.be'"))
                .containsExactly("Chef d'atelier");
        assertThat(colonne(base, "SELECT description FROM poste_atelier WHERE libelle IN ('Pont 1', 'Pont 2') ORDER BY libelle"))
                .containsExactly("Pont deux colonnes", "Pont élévateur secondaire");
        assertThat(colonne(base, "SELECT libelle FROM service WHERE code = 'PLAQUETTES_AR'"))
                .as("les autres lignes sont corrigées").containsExactly("Remplacement plaquettes arrière");
    }

    @Test
    @DisplayName("graine de démonstration : pièces accentuées et traduites, libellés figés de la graine corrigés")
    void graineDeDemonstration() throws IOException {
        DriverManagerDataSource source = baseNeuve();
        Flyway.configure().dataSource(source)
                .locations("classpath:db/migration", "classpath:db/demo").load().migrate();
        JdbcTemplate base = new JdbcTemplate(source);

        assertThat(colonne(base, "SELECT libelle FROM piece ORDER BY reference_fabricant"))
                .containsExactly("Batterie 72 Ah", "Filtre à air", "Filtre à huile", "Huile moteur 5W30 (5 L)",
                        "Jeu de plaquettes avant", "Pneu 205/55 R16");
        assertThat(colonne(base, "SELECT libelle FROM piece WHERE libelle_nl IS NULL OR libelle_en IS NULL "
                + "OR description_nl IS NULL OR description_en IS NULL")).as("pièces sans traduction").isEmpty();
        assertThat(colonne(base, "SELECT libelle FROM piece WHERE libelle ~ ? OR description ~ ?",
                SANS_ACCENTS, SANS_ACCENTS)).isEmpty();
        assertThat(lignesSansAccents(base)).isEmpty();

        // Base de démonstration antérieure à V67 : V900 y a figé les anciens libellés.
        // Une ligne de vente réelle porte le même texte, elle ne doit pas bouger.
        base.update("UPDATE ligne_panier SET libelle_fige = 'Filtre a huile' WHERE libelle_fige = 'Filtre à huile'");
        base.update("UPDATE ligne_intervention SET libelle_fige = 'Revision annuelle' WHERE libelle_fige = 'Révision annuelle'");
        Long reelle = base.queryForObject("SELECT min(id) FROM ligne_intervention WHERE libelle_fige = 'Filtre à huile'", Long.class);
        base.update("UPDATE ligne_intervention SET libelle_fige = 'Filtre a huile', created_by = 'atelier@garage.be' WHERE id = ?", reelle);
        assertThat(lignesSansAccents(base)).hasSizeGreaterThanOrEqualTo(3);

        base.execute(new ClassPathResource("db/demo/V905__libelles_de_la_graine_accentues_et_traduits.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(lignesSansAccents(base)).as("seule la ligne qui n'est pas de la graine garde son texte")
                .containsExactly("Filtre a huile");
        assertThat(colonne(base, "SELECT created_by FROM ligne_intervention WHERE libelle_fige = 'Filtre a huile'"))
                .containsExactly("atelier@garage.be");
    }

    @Test
    @DisplayName("graine de démonstration : textes libres accentués par V906, textes repris depuis laissés tels quels")
    void graineDeDemonstration_textesLibres() throws IOException {
        DriverManagerDataSource source = baseNeuve();
        Flyway.configure().dataSource(source)
                .locations("classpath:db/migration", "classpath:db/demo").load().migrate();
        JdbcTemplate base = new JdbcTemplate(source);

        assertThat(textesLibresSansAccents(base)).isEmpty();
        assertThat(colonne(base, "SELECT titre FROM notification WHERE created_by = 'demo' ORDER BY titre"))
                .contains("Commande payée", "Intervention terminée", "Rendez-vous confirmé");
        assertThat(colonne(base, "SELECT corps FROM message WHERE corps LIKE '%usure%'"))
                .containsExactly("Bonjour, elles sont sous la cote d'usure minimale. Le devis est en attente de votre accord.");

        // Base de démonstration antérieure à V906 : la graine y porte ses textes d'origine.
        // Un membre a écrit depuis le même texte dans un message à lui, il ne doit pas bouger.
        base.update("UPDATE avis SET commentaire = "
                + "'Revision faite dans les temps, explications claires sur les points controles.' WHERE created_by = 'demo'");
        base.update("UPDATE notification SET titre = 'Commande payee' WHERE titre = 'Commande payée'");
        base.update("UPDATE message SET corps = 'Bonjour, les plaquettes sont-elles vraiment a remplacer maintenant ?', "
                + "created_by = 'marie@exemple.be' WHERE corps LIKE '%vraiment à remplacer%'");
        assertThat(textesLibresSansAccents(base)).hasSize(3);

        base.execute(new ClassPathResource("db/demo/V906__textes_libres_de_la_graine_accentues.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(textesLibresSansAccents(base)).as("seul le message qui n'est plus de la graine garde son texte")
                .containsExactly("Bonjour, les plaquettes sont-elles vraiment a remplacer maintenant ?");
    }

    /** Textes libres de la graine encore écrits sans accents ou sans apostrophe. */
    private static List<String> textesLibresSansAccents(JdbcTemplate base) {
        String motif = "(Revision|electronique| a l avant| d un | d usure|ferme ce|reapprovisionnement|realise|"
                + "Depassement| a remplacer|controles|confirme$|terminee$|payee$)";
        return colonne(base, "SELECT commentaire FROM rdv WHERE commentaire ~ ? "
                + "UNION ALL SELECT motif_refus FROM rdv WHERE motif_refus ~ ? "
                + "UNION ALL SELECT commentaire_admin FROM intervention WHERE commentaire_admin ~ ? "
                + "UNION ALL SELECT motif FROM historique_statut_intervention WHERE motif ~ ? "
                + "UNION ALL SELECT commentaire FROM avis WHERE commentaire ~ ? "
                + "UNION ALL SELECT corps FROM message WHERE corps ~ ? "
                + "UNION ALL SELECT titre FROM notification WHERE titre ~ ?",
                motif, motif, motif, motif, motif, motif, motif);
    }

    private static List<String> lignesSansAccents(JdbcTemplate base) {
        return colonne(base, "SELECT libelle_fige FROM ligne_panier WHERE libelle_fige ~ ? "
                + "UNION ALL SELECT libelle_fige FROM ligne_intervention WHERE libelle_fige ~ ?", SANS_ACCENTS, SANS_ACCENTS);
    }
}
