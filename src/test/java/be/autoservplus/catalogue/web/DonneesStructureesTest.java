package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.reservation.service.dto.PlageVue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Données structurées d'une fiche du catalogue (JSON-LD)")
class DonneesStructureesTest {

    /** Ce qu'un libellé saisi au back-office peut contenir de pire pour le bloc. */
    private static final String HOSTILE = "Filtre 5\" \\ serie </script><script>alert(1)</script> & co";

    private static final IdentiteGarage GARAGE = new IdentiteGarage("Bielle Bleue", "Bielle Bleue", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique", "0123.456.789", "RPM Bruxelles",
            "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00", "atelier@bielle-bleue.test");

    private static ArticleVue article(String libelle, String description, String marque, boolean disponible) {
        return new ArticleVue(UUID.randomUUID(), "REF-1", libelle, description, "Filtration",
                new BigDecimal("16.52"), new BigDecimal("19.99"), null, marque, disponible);
    }

    @Test
    @DisplayName("guillemet, barre oblique inverse et </script> : JSON valide, valeur intacte, bloc jamais fermé")
    void valeurHostile() throws Exception {
        for (String json : new String[]{
                DonneesStructurees.dePiece(article(HOSTILE, HOSTILE, HOSTILE, true), HOSTILE, GARAGE),
                DonneesStructurees.dePrestation(article(HOSTILE, HOSTILE, null, true), GARAGE)}) {
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
                DonneesStructurees.dePiece(article("Filtre à huile", "Pour moteur diesel", "Bosch", true), "Pièces auto", GARAGE));
        assertThat(enStock.get("@type").asText()).isEqualTo("Product");
        assertThat(enStock.get("sku").asText()).isEqualTo("REF-1");
        assertThat(enStock.get("category").asText()).isEqualTo("Pièces auto > Filtration");
        assertThat(enStock.get("brand").get("name").asText()).isEqualTo("Bosch");
        assertThat(enStock.get("offers").get("price").asText()).isEqualTo("19.99");
        assertThat(enStock.get("offers").get("availability").asText()).isEqualTo("https://schema.org/InStock");
        assertThat(enStock.get("offers").get("seller").get("name").asText()).isEqualTo("Bielle Bleue");

        JsonNode epuisee = new ObjectMapper().readTree(
                DonneesStructurees.dePiece(article("Filtre à huile", null, null, false), "Pièces auto", GARAGE));
        assertThat(epuisee.get("offers").get("availability").asText()).isEqualTo("https://schema.org/OutOfStock");
        assertThat(epuisee.has("brand")).isFalse();
        assertThat(epuisee.has("description")).isFalse();
    }

    @Test
    @DisplayName("prestation : service, catégorie, prestataire et prix TVAC")
    void prestation() throws Exception {
        JsonNode service = new ObjectMapper().readTree(
                DonneesStructurees.dePrestation(article("Vidange", "Huile et filtre", null, true), GARAGE));
        assertThat(service.get("@type").asText()).isEqualTo("Service");
        assertThat(service.get("serviceType").asText()).isEqualTo("Filtration");
        assertThat(service.get("provider").get("name").asText()).isEqualTo("Bielle Bleue");
        assertThat(service.get("provider").get("address").get("addressCountry").asText()).isEqualTo("BE");
        assertThat(service.get("provider").get("telephone").asText()).isEqualTo("+32 2 000 00 00");
        assertThat(service.get("offers").get("price").asText()).isEqualTo("19.99");
    }

    @Test
    @DisplayName("aucune offre n'est plus attribuée au logiciel : le nom du garage, jamais AutoServ+")
    void vendeurLuDansLaConfiguration() {
        assertThat(DonneesStructurees.dePiece(article("Filtre", null, null, true), "Pièces auto", GARAGE))
                .doesNotContain("AutoServ");
        assertThat(DonneesStructurees.dePrestation(article("Vidange", null, null, true), GARAGE))
                .doesNotContain("AutoServ");
    }

    @Test
    @DisplayName("garage : AutoRepair, coordonnées, horaires regroupés par heures identiques")
    void garage() throws Exception {
        List<PlageVue> plages = List.of(
                new PlageVue(1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), true),
                new PlageVue(2L, DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(18, 0), true),
                new PlageVue(3L, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), true),
                new PlageVue(4L, DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true));
        JsonNode garage = new ObjectMapper().readTree(
                DonneesStructurees.duGarage(GARAGE, plages, "https://exemple.test/"));

        assertThat(garage.get("@context").asText()).isEqualTo("https://schema.org");
        assertThat(garage.get("@type").asText()).isEqualTo("AutoRepair");
        assertThat(garage.get("name").asText()).isEqualTo("Bielle Bleue");
        assertThat(garage.get("url").asText()).isEqualTo("https://exemple.test/");
        assertThat(garage.get("email").asText()).isEqualTo("atelier@bielle-bleue.test");
        JsonNode adresse = garage.get("address");
        assertThat(adresse.get("streetAddress").asText()).isEqualTo("Rue de l'Atelier 12");
        assertThat(adresse.get("postalCode").asText()).isEqualTo("1000");
        assertThat(adresse.get("addressLocality").asText()).isEqualTo("Bruxelles");
        assertThat(garage.has("aggregateRating")).isFalse();

        JsonNode horaires = garage.get("openingHoursSpecification");
        assertThat(horaires).hasSize(3);
        assertThat(horaires.get(0).get("dayOfWeek").get(0).asText()).isEqualTo("https://schema.org/Monday");
        assertThat(horaires.get(0).get("dayOfWeek").get(1).asText()).isEqualTo("https://schema.org/Tuesday");
        assertThat(horaires.get(0).get("opens").asText()).isEqualTo("08:00");
        assertThat(horaires.get(0).get("closes").asText()).isEqualTo("12:00");
        assertThat(horaires.get(2).get("dayOfWeek").get(0).asText()).isEqualTo("https://schema.org/Saturday");
    }

    @Test
    @DisplayName("garage sans plage active : aucune spécification d'horaires vide")
    void garageSansHoraires() throws Exception {
        JsonNode garage = new ObjectMapper().readTree(DonneesStructurees.duGarage(GARAGE, List.of(), null));
        assertThat(garage.has("openingHoursSpecification")).isFalse();
        assertThat(garage.has("url")).isFalse();
    }
}
