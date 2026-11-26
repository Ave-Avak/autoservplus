package be.autoservplus.intervention.web;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.domain.UniteLigne;
import be.autoservplus.intervention.service.dto.CarnetEntretien;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CarnetEntretienJsonLd")
class CarnetEntretienJsonLdTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage("Bielle Bleue", "Bielle Bleue", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique", "0123.456.789", "RPM Bruxelles",
            "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00", "atelier@bielle-bleue.test");

    private static final UUID VEHICULE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INTERVENTION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime DEBUT = OffsetDateTime.of(2026, 9, 14, 9, 0, 0, 0, ZoneOffset.ofHours(2));

    private final CarnetEntretienJsonLd redacteur = redacteur();

    private static CarnetEntretienJsonLd redacteur() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("carnet.piece-incluse", Locale.FRENCH, "Pièce comprise dans le forfait");
        return new CarnetEntretienJsonLd(GARAGE, messages, "https://garage.test");
    }

    private static CarnetEntretien carnet(StatutIntervention statut, OffsetDateTime fin, Integer kilometrage) {
        return new CarnetEntretien(VEHICULE, "1-ABC-123", "Volkswagen", "Golf", (short) 2018,
                "WVWZZZ1KZAW000001", kilometrage, List.of(new CarnetEntretien.Entree(
                INTERVENTION, "INT-2026-0001", statut, DEBUT, fin,
                new BigDecimal("100.00"), new BigDecimal("121.00"), List.of(
                new CarnetEntretien.Ligne(CarnetEntretien.Nature.PRESTATION, "Vidange", null,
                        new BigDecimal("1"), UniteLigne.FORFAIT),
                new CarnetEntretien.Ligne(CarnetEntretien.Nature.PIECE_INCLUSE, "Filtre à huile", "OC-90",
                        new BigDecimal("1"), UniteLigne.PIECE),
                new CarnetEntretien.Ligne(CarnetEntretien.Nature.PRESTATION, "Diagnostic", null,
                        new BigDecimal("1.5"), UniteLigne.HEURE)))));
    }

    private String json(CarnetEntretien carnet) {
        return new String(redacteur.rediger(carnet, Locale.FRENCH), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("schema.org seul : un Vehicle, le garage en AutoRepair, une Action par intervention")
    void graphe() {
        String json = json(carnet(StatutIntervention.TERMINEE, DEBUT.plusHours(2), 123_456));

        assertThat((String) JsonPath.read(json, "$['@context']")).isEqualTo("https://schema.org");
        Map<String, Object> vehicule = JsonPath.read(json, "$['@graph'][0]");
        assertThat(vehicule).containsEntry("@type", "Vehicle")
                .containsEntry("@id", "urn:uuid:" + VEHICULE)
                .containsEntry("vehicleIdentificationNumber", "WVWZZZ1KZAW000001")
                .containsEntry("vehicleModelDate", "2018");
        assertThat((Integer) JsonPath.read(json, "$['@graph'][0].mileageFromOdometer.value")).isEqualTo(123_456);
        assertThat((String) JsonPath.read(json, "$['@graph'][0].mileageFromOdometer.unitCode")).isEqualTo("KMT");
        assertThat((String) JsonPath.read(json, "$['@graph'][1]['@type']")).isEqualTo("AutoRepair");
        assertThat((String) JsonPath.read(json, "$['@graph'][1].vatID")).isEqualTo("BE0123456789");

        Map<String, Object> action = JsonPath.read(json, "$['@graph'][2]");
        assertThat(action).containsEntry("@type", "Action")
                .containsEntry("actionStatus", "https://schema.org/CompletedActionStatus")
                .containsEntry("startTime", "2026-09-14T09:00:00+02:00")
                .containsEntry("endTime", "2026-09-14T11:00:00+02:00");
        assertThat((String) JsonPath.read(json, "$['@graph'][2].object['@id']")).isEqualTo("urn:uuid:" + VEHICULE);
        assertThat((String) JsonPath.read(json, "$['@graph'][2].agent['@id']")).isEqualTo("https://garage.test/");
    }

    @Test
    @DisplayName("les travaux : services et pièces avec leur unité, totaux hors TVA et TVA comprise")
    void travauxEtTotaux() {
        String json = json(carnet(StatutIntervention.TERMINEE, DEBUT.plusHours(2), null));

        String commande = "$['@graph'][2].result";
        assertThat((String) JsonPath.read(json, commande + ".orderStatus")).isEqualTo("https://schema.org/OrderDelivered");
        assertThat((String) JsonPath.read(json, commande + ".orderedItem[0].orderedItem['@type']")).isEqualTo("Service");
        assertThat((String) JsonPath.read(json, commande + ".orderedItem[1].orderedItem['@type']")).isEqualTo("Product");
        assertThat((String) JsonPath.read(json, commande + ".orderedItem[1].orderedItem.mpn")).isEqualTo("OC-90");
        assertThat((String) JsonPath.read(json, commande + ".orderedItem[1].orderedItem.description"))
                .isEqualTo("Pièce comprise dans le forfait");
        assertThat((String) JsonPath.read(json, commande + ".orderedItem[2].orderQuantity.unitCode")).isEqualTo("HUR");
        assertThat((List<Boolean>) JsonPath.read(json, commande + ".partOfInvoice.totalPaymentDue[*].valueAddedTaxIncluded"))
                .containsExactly(false, true);
        assertThat((List<Double>) JsonPath.read(json, commande + ".partOfInvoice.totalPaymentDue[*].price"))
                .containsExactly(100.0, 121.0);
        Map<String, Object> vehicule = JsonPath.read(json, "$['@graph'][0]");
        assertThat(vehicule).doesNotContainKey("mileageFromOdometer");
    }

    @Test
    @DisplayName("une intervention en cours n'a ni fin ni statut accompli")
    void enCours() {
        String json = json(carnet(StatutIntervention.EN_COURS, null, null));

        assertThat((String) JsonPath.read(json, "$['@graph'][2].actionStatus"))
                .isEqualTo("https://schema.org/ActiveActionStatus");
        Map<String, Object> action = JsonPath.read(json, "$['@graph'][2]");
        assertThat(action).doesNotContainKey("endTime");
        assertThat((String) JsonPath.read(json, "$['@graph'][2].result.orderStatus"))
                .isEqualTo("https://schema.org/OrderProcessing");
    }

    @Test
    @DisplayName("nom du fichier tiré de la plaque, sans caractère douteux")
    void nomFichier() {
        assertThat(CarnetEntretienJsonLd.nomFichier(carnet(StatutIntervention.TERMINEE, null, null)))
                .isEqualTo("carnet-entretien-1-ABC-123.jsonld");
    }
}
