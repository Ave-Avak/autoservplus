package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Rayon;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recherche du catalogue contre un vrai PostgreSQL : l'extension {@code unaccent} de V62,
 * la clause d'échappement et la requête de comptage n'existent que là. Un dépôt simulé
 * ne prouverait rien de la requête.
 *
 * <p>Chaque ligne porte le marqueur « zqx », absent du catalogue de référence : les
 * recherches du test ne ramènent que ses propres lignes.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("Recherche du catalogue (intégration)")
class RechercheCatalogueIT extends SocleIntegration {

    @Autowired private CatalogueService catalogue;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void semer() {
        jdbc.update("INSERT INTO categorie (code, libelle, type, rayon) VALUES ('ZQX_PIECES', 'Éclairage zqx', 'PIECE', 'PIECES_AUTO')");
        jdbc.update("INSERT INTO categorie (code, libelle, type) VALUES ('ZQX_SERVICES', 'Géométrie zqx', 'SERVICE')");
        jdbc.update("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, description, marque, prix_htva, quantite_stock)
                VALUES ((SELECT id FROM categorie WHERE code = 'ZQX_PIECES'), 'ZQX-001', 'Ampoule zqx',
                        'Pour phare antibrouillard à réflecteur', 'Lumière Dorée', 10.00, 5)
                """);
        jdbc.update("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, description, marque, prix_htva, quantite_stock)
                VALUES ((SELECT id FROM categorie WHERE code = 'ZQX_PIECES'), 'ZQX-002', 'Additif zqx 100% synthèse',
                        NULL, NULL, 12.00, 5)
                """);
        jdbc.update("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, description, marque, prix_htva, quantite_stock, actif)
                VALUES ((SELECT id FROM categorie WHERE code = 'ZQX_PIECES'), 'ZQX-003', 'Ampoule zqx retirée',
                        NULL, NULL, 12.00, 5, false)
                """);
        jdbc.update("""
                INSERT INTO service (categorie_id, code, libelle, description, prix_htva, duree_minutes)
                VALUES ((SELECT id FROM categorie WHERE code = 'ZQX_SERVICES'), 'ZQX_PARALLELISME', 'Réglage zqx du parallélisme',
                        'Contrôle des trains roulants', 80.00, 45)
                """);
    }

    @Test
    @DisplayName("un terme sans accent trouve un libellé accentué, et l'inverse")
    void accentsIgnores() {
        assertThat(libelles(catalogue.rechercherPrestations("reglage zqx", 0)))
                .containsExactly("Réglage zqx du parallélisme");
        assertThat(libelles(catalogue.rechercherPrestations("PARALLELISME", 0)))
                .containsExactly("Réglage zqx du parallélisme");
        assertThat(libelles(catalogue.rechercherProduits("àmpoule zqx", Rayon.PIECES_AUTO, 0)))
                .containsExactly("Ampoule zqx");
    }

    @Test
    @DisplayName("la description d'une pièce est cherchée, comme celle d'une prestation")
    void descriptionDesPieces() {
        assertThat(libelles(catalogue.rechercherProduits("antibrouillard a reflecteur", Rayon.PIECES_AUTO, 0)))
                .containsExactly("Ampoule zqx");
        assertThat(libelles(catalogue.rechercherPrestations("trains roulants", 0)))
                .containsExactly("Réglage zqx du parallélisme");
    }

    @Test
    @DisplayName("le libellé de la catégorie ramène ses articles actifs")
    void libelleDeLaCategorie() {
        assertThat(libelles(catalogue.rechercherProduits("eclairage zqx", Rayon.PIECES_AUTO, 0)))
                .containsExactly("Additif zqx 100% synthèse", "Ampoule zqx");
        assertThat(libelles(catalogue.rechercherPrestations("geometrie zqx", 0)))
                .containsExactly("Réglage zqx du parallélisme");
    }

    @Test
    @DisplayName("plusieurs mots se cherchent chacun, à des endroits différents de l'article")
    void motsSepares() {
        assertThat(libelles(catalogue.rechercherPrestations("parallelisme roulants", 0)))
                .containsExactly("Réglage zqx du parallélisme");
        assertThat(libelles(catalogue.rechercherProduits("ampoule eclairage", Rayon.PIECES_AUTO, 0)))
                .containsExactly("Ampoule zqx");
        assertThat(libelles(catalogue.rechercherPrestations("parallelisme zqxabsent", 0))).isEmpty();
    }

    @Test
    @DisplayName("la marque et la référence du fabricant restent cherchées")
    void marqueEtReference() {
        assertThat(libelles(catalogue.rechercherProduits("lumiere doree", Rayon.PIECES_AUTO, 0))).containsExactly("Ampoule zqx");
        assertThat(libelles(catalogue.rechercherProduits("zqx-001", Rayon.PIECES_AUTO, 0))).containsExactly("Ampoule zqx");
    }

    @Test
    @DisplayName("un joker saisi est cherché comme un caractère")
    void jokersNeutralises() {
        assertThat(libelles(catalogue.rechercherProduits("zqx 100%", Rayon.PIECES_AUTO, 0)))
                .containsExactly("Additif zqx 100% synthèse");
        assertThat(catalogue.rechercherProduits("zqx 1_0", Rayon.PIECES_AUTO, 0)).isEmpty();
        assertThat(catalogue.rechercherProduits("%%", Rayon.PIECES_AUTO, 0)).isEmpty();
        assertThat(catalogue.rechercherProduits("z!x", Rayon.PIECES_AUTO, 0)).isEmpty();
    }

    @Test
    @DisplayName("une page demandée après la dernière rend la dernière, avec le bon total")
    void pageAuDelaDeLaDerniere() {
        Page<ArticleVue> page = catalogue.rechercherProduits("zqx", Rayon.PIECES_AUTO, 7);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
        assertThat(libelles(page)).containsExactly("Additif zqx 100% synthèse", "Ampoule zqx");
    }

    private static java.util.List<String> libelles(Page<ArticleVue> page) {
        return page.getContent().stream().map(ArticleVue::libelle).toList();
    }
}
