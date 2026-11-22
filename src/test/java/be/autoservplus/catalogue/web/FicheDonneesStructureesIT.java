package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.support.SocleIntegration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le bloc JSON-LD d'une fiche, tel que la page le sert.
 *
 * <p>{@code DonneesStructureesTest} prouve la sérialisation. Ce cas prouve que le gabarit
 * l'insère sans la récrire : un libellé qui ferme la balise ne doit ajouter aucun script
 * à la page, et le bloc doit se relire en JSON.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Fiche du catalogue : données structurées (intégration)")
class FicheDonneesStructureesIT extends SocleIntegration {

    private static final String HOSTILE = "Filtre 5\" \\ serie </script><script>alert(1)</script>";

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("pièce au libellé hostile : un seul script de données, JSON valide, aucun script en plus")
    void pieceAuLibelleHostile() throws Exception {
        Categorie categorie = categories.save(new Categorie("IT-LD", "Filtration du test", TypeCategorie.PIECE));
        Piece piece = new Piece(categorie, "IT-LD-1", HOSTILE, new BigDecimal("19.99"));
        piece.setDescription(HOSTILE);
        piece.setQuantiteStock(3);
        piece = pieces.saveAndFlush(piece);

        JsonNode donnees = donneesStructurees("/pieces/" + piece.getReference());
        assertThat(donnees.get("@type").asText()).isEqualTo("Product");
        assertThat(donnees.get("name").asText()).isEqualTo(HOSTILE);
        assertThat(donnees.get("description").asText()).isEqualTo(HOSTILE);
    }

    @Test
    @DisplayName("prestation : le bloc de la fiche se relit en JSON")
    void prestation() throws Exception {
        UUID prestation = jdbc.queryForObject(
                "SELECT reference FROM service WHERE actif AND deleted_at IS NULL LIMIT 1", UUID.class);

        assertThat(donneesStructurees("/services/" + prestation).get("@type").asText()).isEqualTo("Service");
    }

    /** Le bloc JSON-LD de la page, après avoir vérifié qu'elle ne porte aucun autre script en ligne. */
    private JsonNode donneesStructurees(String adresse) throws Exception {
        String html = mvc.perform(get(adresse).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);
        Elements blocs = page.select("script[type=application/ld+json]");
        assertThat(blocs).as("blocs JSON-LD").hasSize(1);
        assertThat(page.select("script:not([src]):not([type=application/ld+json])"))
                .as("scripts en ligne : le libellé n'a pas fermé le bloc").isEmpty();
        assertThat(html).doesNotContain("alert(1)</script>");
        return new ObjectMapper().readTree(blocs.first().data());
    }
}
