package be.autoservplus.api.web;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.reservation.service.DisponibilitesPubliquesService;
import be.autoservplus.reservation.service.dto.DisponibilitesPubliques;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ApiDisponibilitesController")
class ApiDisponibilitesControllerTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage("Bielle Bleue", "Bielle Bleue", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique", "0123.456.789", "RPM Bruxelles",
            "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00", "atelier@bielle-bleue.test");

    private final DisponibilitesPubliquesService service = mock(DisponibilitesPubliquesService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new ApiDisponibilitesController(service, GARAGE, new ObjectMapper(), "https://garage.test")).build();
        Instant debut = Instant.parse("2026-09-15T07:00:00Z");
        when(service.prochainsJours(any())).thenReturn(new DisponibilitesPubliques(Duration.ofMinutes(30),
                ZoneId.of("Europe/Brussels"), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 13),
                List.of(new DisponibilitesPubliques.Creneau(debut, debut.plus(Duration.ofMinutes(30))))));
    }

    @Test
    @DisplayName("JSON-LD : ItemList de Schedule à l'heure du garage, liens HAL, aucun poste")
    void jsonLd() throws Exception {
        mvc.perform(get("/api/v1/disponibilites"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
                .andExpect(jsonPath("$['@context']").value("https://schema.org"))
                .andExpect(jsonPath("$['@type']").value("ItemList"))
                .andExpect(jsonPath("$.numberOfItems").value(1))
                .andExpect(jsonPath("$.itemListElement[0]['@type']").value("Schedule"))
                .andExpect(jsonPath("$.itemListElement[0].startDate").value("2026-09-15T09:00:00+02:00"))
                .andExpect(jsonPath("$.itemListElement[0].endDate").value("2026-09-15T09:30:00+02:00"))
                .andExpect(jsonPath("$.itemListElement[0].duration").value("PT30M"))
                .andExpect(jsonPath("$.itemListElement[0].scheduleTimezone").value("Europe/Brussels"))
                .andExpect(jsonPath("$._links.garage.href").value("https://garage.test/api/v1/garages/0123456789"))
                .andExpect(content().string(not(containsString("poste"))));
    }

    @Test
    @DisplayName("cache public d'une minute, ETag, et 304 quand le client renvoie le même ETag")
    void etag() throws Exception {
        String etag = mvc.perform(get("/api/v1/disponibilites"))
                .andExpect(header().string("Cache-Control", "max-age=60, public"))
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get("/api/v1/disponibilites").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("les prestations demandées passent au service, le lien self garde la requête")
    void prestations() throws Exception {
        UUID vidange = UUID.randomUUID();

        mvc.perform(get("/api/v1/disponibilites?prestation={reference}", vidange))
                .andExpect(jsonPath("$._links.self.href")
                        .value("https://garage.test/api/v1/disponibilites?prestation=" + vidange));
        verify(service).prochainsJours(List.of(vidange));
    }
}
