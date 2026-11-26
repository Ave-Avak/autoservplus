package be.autoservplus.catalogue.web;

import be.autoservplus.support.SocleIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les quatre onglets du catalogue : une liste par rayon, la fiche d'un produit sous le
 * préfixe de son rayon, une redirection permanente depuis les autres.
 *
 * <p>Un article de chaque rayon porte le marqueur « zqy », absent du catalogue de
 * référence.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Rayons du catalogue (intégration)")
class RayonsCatalogueIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void semer() {
        semer("ZQY_PIECES", "Freinage zqy", "PIECES_AUTO", "ZQY-P", "Plaquettes zqy");
        semer("ZQY_ACCESSOIRES", "Intérieur zqy", "ACCESSOIRES", "ZQY-A", "Tapis zqy");
        semer("ZQY_ENTRETIEN", "Lavage zqy", "ENTRETIEN", "ZQY-E", "Éponge zqy");
    }

    private void semer(String code, String libelle, String rayon, String reference, String article) {
        jdbc.update("INSERT INTO categorie (code, libelle, type, rayon) VALUES (?, ?, 'PIECE', ?)",
                code, libelle, rayon);
        jdbc.update("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva, quantite_stock)
                VALUES ((SELECT id FROM categorie WHERE code = ?), ?, ?, 10.00, 3)
                """, code, reference, article);
    }

    private UUID reference(String referenceFabricant) {
        return jdbc.queryForObject("SELECT reference FROM piece WHERE reference_fabricant = ?",
                UUID.class, referenceFabricant);
    }

    @ParameterizedTest(name = "{0} ne liste que son rayon")
    @CsvSource({
            "/pieces, Plaquettes zqy, Tapis zqy, Éponge zqy",
            "/accessoires, Tapis zqy, Plaquettes zqy, Éponge zqy",
            "/entretien, Éponge zqy, Plaquettes zqy, Tapis zqy"})
    void listeDuRayon(String adresse, String present, String absent1, String absent2) throws Exception {
        Document page = Jsoup.parse(ouvrir(adresse, Locale.FRENCH));

        assertThat(page.select(".grille-articles").text()).contains(present)
                .doesNotContain(absent1).doesNotContain(absent2);
        assertThat(page.select("nav.onglets-catalogue a[aria-current=page]").attr("href")).isEqualTo(adresse);
        assertThat(page.select("nav.onglets-catalogue a")).extracting(lien -> lien.attr("href"))
                .containsExactly("/services", "/pieces", "/accessoires", "/entretien");
    }

    @Test
    @DisplayName("le filtre d'un rayon ne propose que ses catégories, et ignore celle d'un autre")
    void filtreParCategorie() throws Exception {
        Document accessoires = Jsoup.parse(ouvrir("/accessoires", Locale.FRENCH));
        assertThat(accessoires.select("nav.filtres-catalogue").text()).contains("Intérieur zqy").doesNotContain("Freinage zqy");

        Document intrus = Jsoup.parse(ouvrir("/accessoires?categorie=ZQY_PIECES", Locale.FRENCH));
        assertThat(intrus.select(".grille-articles")).isEmpty();
    }

    @Test
    @DisplayName("les onglets se traduisent")
    void ongletsTraduits() throws Exception {
        String neerlandais = ouvrir("/entretien", Locale.of("nl"));
        // Jsoup retire le trait d'union conditionnel de text() : il se lit dans la page servie.
        assertThat(Jsoup.parse(neerlandais).select("nav.onglets-catalogue").text())
                .isEqualTo("Diensten Auto-onderdelen Accessoires Onderhoudsproducten");
        assertThat(neerlandais).as("coupure proposée au navigateur à 320 px")
                .containsAnyOf("Onderhouds\u00ADproducten", "Onderhouds&shy;producten");
        assertThat(Jsoup.parse(ouvrir("/services", Locale.ENGLISH)).select("nav.onglets-catalogue").text())
                .isEqualTo("Services Car parts Accessories Car care products");
    }

    @ParameterizedTest(name = "fiche de {1} : {0}, lien canonique et fil d'Ariane")
    @CsvSource({
            "ZQY-P, pieces, Pièces auto",
            "ZQY-A, accessoires, Accessoires",
            "ZQY-E, entretien, Produits d'entretien"})
    void ficheSousSonRayon(String referenceFabricant, String prefixe, String liste) throws Exception {
        String adresse = "/" + prefixe + "/" + reference(referenceFabricant);
        Document fiche = Jsoup.parse(ouvrir(adresse, Locale.FRENCH));

        assertThat(fiche.select("link[rel=canonical]").attr("href")).isEqualTo(adresse);
        assertThat(fiche.select("nav.fil-ariane a")).extracting(lien -> lien.text())
                .containsExactly("Accueil", liste);
        assertThat(fiche.select("script[type=application/ld+json]").html()).contains("\"category\"");
    }

    @ParameterizedTest(name = "{0} sous /{1} renvoie en 301 vers /{2}")
    @CsvSource({
            "ZQY-A, pieces, accessoires",
            "ZQY-A, entretien, accessoires",
            "ZQY-E, pieces, entretien",
            "ZQY-E, accessoires, entretien",
            "ZQY-P, accessoires, pieces",
            "ZQY-P, entretien, pieces"})
    void redirectionPermanente(String referenceFabricant, String mauvais, String bon) throws Exception {
        UUID reference = reference(referenceFabricant);

        mvc.perform(get("/" + mauvais + "/" + reference))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/" + bon + "/" + reference));
    }

    @Test
    @DisplayName("une référence inconnue répond 404 sous chaque préfixe, sans redirection")
    void referenceInconnue() throws Exception {
        for (String prefixe : new String[]{"pieces", "accessoires", "entretien"}) {
            mvc.perform(get("/" + prefixe + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("/catalogue renvoie vers les prestations")
    void catalogue() throws Exception {
        mvc.perform(get("/catalogue")).andExpect(status().isFound()).andExpect(redirectedUrl("/services"));
    }

    @Test
    @DisplayName("la recherche range les résultats en quatre sections, dans l'ordre des onglets")
    void rechercheEnQuatreSections() throws Exception {
        Document page = Jsoup.parse(ouvrir("/recherche?q=zqy", Locale.FRENCH));

        assertThat(page.select("main section")).extracting(section -> section.id())
                .containsExactly("pieces", "accessoires", "entretien");
        assertThat(page.select("#accessoires a").attr("href")).isEqualTo("/accessoires/" + reference("ZQY-A"));
        assertThat(page.select("#entretien h2").text()).isEqualTo("Produits d'entretien (1)");
    }

    private String ouvrir(String adresse, Locale langue) throws Exception {
        return mvc.perform(get(adresse).locale(langue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
