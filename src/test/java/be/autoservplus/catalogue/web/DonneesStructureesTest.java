package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.dto.ArticleVue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Données structurées d'une fiche du catalogue (JSON-LD)")
class DonneesStructureesTest {

    /** Ce qu'un libellé saisi au back-office peut contenir de pire pour le bloc. */
    private static final String HOSTILE = "Filtre 5\" \\ serie </script><script>alert(1)</script> & co";

    private static ArticleVue article(String libelle, String description, String marque, boolean disponible) {
        return new ArticleVue(UUID.randomUUID(), "REF-1", libelle, description, "Filtration",
                new BigDecimal("16.52"), new BigDecimal("19.99"), null, marque, disponible);
    }

    @Test
    @DisplayName("guillemet, barre oblique inverse et </script> : JSON valide, valeur intacte, bloc jamais fermé")
    void valeurHostile() throws Exception {
        for (String json : new String[]{
                DonneesStructurees.dePiece(article(HOSTILE, HOSTILE, HOSTILE, true)),
                DonneesStructurees.dePrestation(article(HOSTILE, HOSTILE, null, true))}) {
            assertThat(json).doesNotContain("<").doesNotContain(">").doesNotContain("&");
            assertThat(json.toLowerCase()).doesNotContain("</script");

            JsonNode relu = new ObjectMapper().readTree(json);
            assertThat(relu.get("name").asText()).isEqualTo(HOSTILE);
            assertThat(relu.get("description").asText()).isEqualTo(HOSTILE);
        }
    }

    @Test
    @DisplayName("pièce : produit, prix TVAC, disponibilité selon le stock, marque seulement si elle existe")
    void piece() throws Exception {
        JsonNode enStock = new ObjectMapper().readTree(
                DonneesStructurees.dePiece(article("Filtre à huile", "Pour moteur diesel", "Bosch", true)));
        assertThat(enStock.get("@type").asText()).isEqualTo("Product");
        assertThat(enStock.get("sku").asText()).isEqualTo("REF-1");
        assertThat(enStock.get("brand").get("name").asText()).isEqualTo("Bosch");
        assertThat(enStock.get("offers").get("price").asText()).isEqualTo("19.99");
        assertThat(enStock.get("offers").get("availability").asText()).isEqualTo("https://schema.org/InStock");

        JsonNode epuisee = new ObjectMapper().readTree(
                DonneesStructurees.dePiece(article("Filtre à huile", null, null, false)));
        assertThat(epuisee.get("offers").get("availability").asText()).isEqualTo("https://schema.org/OutOfStock");
        assertThat(epuisee.has("brand")).isFalse();
        assertThat(epuisee.has("description")).isFalse();
    }

    @Test
    @DisplayName("prestation : service, catégorie, prestataire et prix TVAC")
    void prestation() throws Exception {
        JsonNode service = new ObjectMapper().readTree(
                DonneesStructurees.dePrestation(article("Vidange", "Huile et filtre", null, true)));
        assertThat(service.get("@type").asText()).isEqualTo("Service");
        assertThat(service.get("serviceType").asText()).isEqualTo("Filtration");
        assertThat(service.get("provider").get("address").get("addressCountry").asText()).isEqualTo("BE");
        assertThat(service.get("offers").get("price").asText()).isEqualTo("19.99");
    }
}
