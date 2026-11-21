package be.autoservplus.catalogue.web;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Page de résultats de la recherche (F5), telle qu'elle est rendue.
 *
 * <p>Quatorze pièces et une prestation portent le marqueur « zqx », absent du catalogue
 * de référence : quatorze pièces font deux pages de douze, ce qui suffit à montrer les
 * liens de pagination sans dépendre du nombre de pièces de la base de test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Page de recherche (intégration)")
class RecherchePageIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void semer() {
        jdbc.update("INSERT INTO categorie (code, libelle, type) VALUES ('ZQX_PIECES', 'Visserie zqx', 'PIECE')");
        jdbc.update("INSERT INTO categorie (code, libelle, type) VALUES ('ZQX_SERVICES', 'Contrôle zqx', 'SERVICE')");
        for (int i = 1; i <= 14; i++) {
            jdbc.update("""
                    INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva, quantite_stock)
                    VALUES ((SELECT id FROM categorie WHERE code = 'ZQX_PIECES'), ?, ?, 1234.50, 3)
                    """, "ZQX-%02d".formatted(i), "Écrou zqx %02d".formatted(i));
        }
        jdbc.update("""
                INSERT INTO service (categorie_id, code, libelle, prix_htva, duree_minutes)
                VALUES ((SELECT id FROM categorie WHERE code = 'ZQX_SERVICES'), 'ZQX_SERRAGE', 'Serrage zqx au couple', 40.00, 30)
                """);
    }

    @Test
    @DisplayName("résultats en français : compte, deux sections, prix au format de la langue")
    void resultatsEnFrancais() throws Exception {
        String page = ouvrir("/recherche?q=ecrou zqx", Locale.FRENCH);

        assertThat(page).contains("<title>Recherche : ecrou zqx | AutoServ+</title>");
        assertThat(page).contains("14 résultats pour « ecrou zqx ».");
        assertThat(page).contains("Pièces détachées (14)");
        assertThat(page).doesNotContain("id=\"prestations\"");
        assertThat(page).contains("Écrou zqx 01").contains("Écrou zqx 12").doesNotContain("Écrou zqx 13");
        assertThat(page).contains("1 493,75 €");
    }

    @Test
    @DisplayName("la page se rend en néerlandais et en anglais, titre et prix compris")
    void autresLangues() throws Exception {
        String neerlandais = ouvrir("/recherche?q=zqx", new Locale("nl"));
        assertThat(neerlandais).contains("<title>Zoeken: zqx | AutoServ+</title>");
        assertThat(neerlandais).contains("15 resultaten voor &quot;zqx&quot;.");
        assertThat(neerlandais).contains("Diensten (1)").contains("Onderdelen (14)");
        assertThat(neerlandais).contains("1.493,75 €");
        assertThat(neerlandais).doesNotContain("Recherche").doesNotContain("résultat");

        String anglais = ouvrir("/recherche?q=zqx", Locale.ENGLISH);
        assertThat(anglais).contains("<title>Search: zqx | AutoServ+</title>");
        assertThat(anglais).contains("15 results for &quot;zqx&quot;.");
        assertThat(anglais).contains("1,493.75 €");
    }

    @Test
    @DisplayName("chaque section a ses liens de page, qui gardent le terme et la page de l'autre section")
    void paginationParSection() throws Exception {
        String premiere = ouvrir("/recherche?q=zqx", Locale.FRENCH);
        assertThat(premiere).contains("Page 1 sur 2");
        assertThat(premiere).contains("href=\"/recherche?q=zqx&amp;pagePrestations=1&amp;pagePieces=2#pieces\"");
        assertThat(premiere).doesNotContain("rel=\"prev\"");
        assertThat(premiere).doesNotContain("Pages des prestations trouvées");

        String seconde = ouvrir("/recherche?q=zqx&pagePieces=2", Locale.FRENCH);
        assertThat(seconde).contains("Page 2 sur 2");
        assertThat(seconde).contains("Écrou zqx 13").contains("Écrou zqx 14").doesNotContain("Écrou zqx 12");
        assertThat(seconde).contains("href=\"/recherche?q=zqx&amp;pagePrestations=1&amp;pagePieces=1#pieces\"");
        assertThat(seconde).doesNotContain("rel=\"next\"");
        assertThat(seconde).as("la prestation reste sur sa première page").contains("Serrage zqx au couple");
    }

    @Test
    @DisplayName("sous deux caractères : message relié au champ, aucun résultat ni compte")
    void termeTropCourt() throws Exception {
        String page = ouvrir("/recherche?q=z", Locale.FRENCH);

        assertThat(page).contains("Saisissez au moins 2 caractères.");
        assertThat(page).contains("aria-invalid=\"true\"").contains("aria-describedby=\"recherche-erreur\"");
        assertThat(page).contains("value=\"z\"");
        assertThat(page).doesNotContain("résultat").doesNotContain("class=\"carte\"");

        assertThat(ouvrir("/recherche?q=z", new Locale("nl"))).contains("Voer minstens 2 tekens in.");
    }

    @Test
    @DisplayName("sans terme : le formulaire seul, sans message ni compte")
    void sansTerme() throws Exception {
        String page = ouvrir("/recherche", Locale.FRENCH);

        assertThat(page).contains("<title>Recherche | AutoServ+</title>");
        assertThat(page).doesNotContain("recherche-erreur").doesNotContain("résultat");
        assertThat(ouvrir("/recherche?q=%20%20", Locale.FRENCH)).doesNotContain("recherche-erreur");
    }

    @Test
    @DisplayName("aucun résultat : le message propose les deux catalogues")
    void aucunResultat() throws Exception {
        String page = ouvrir("/recherche?q=introuvable-zqx", Locale.FRENCH);

        assertThat(page).contains("Aucun résultat pour « introuvable-zqx ».");
        assertThat(page).contains("href=\"/services\"").contains("nos pièces");
    }

    @Test
    @DisplayName("le terme saisi est échappé dans la page")
    void termeEchappe() throws Exception {
        String page = ouvrir("/recherche?q=<script>zqx", Locale.FRENCH);

        assertThat(page).doesNotContain("<script>zqx").contains("&lt;script&gt;zqx");
    }

    private String ouvrir(String adresse, Locale langue) throws Exception {
        return mvc.perform(get(adresse).locale(langue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
