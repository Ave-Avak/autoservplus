package be.autoservplus.api.web;

import be.autoservplus.catalogue.service.CatalogueService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.config.IdentiteGarage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Format HAL de l'API publique (F41), sans Spring ni base : le contrôleur seul, monté sur
 * un MockMvc autonome. Les IT de {@code ApiPubliqueIT} couvrent la sécurité et le catalogue
 * réel.
 */
@DisplayName("ApiPubliqueController, format HAL")
class ApiPubliqueControllerTest {

    private static final IdentiteGarage GARAGE = new IdentiteGarage("Bielle Bleue", "Bielle Bleue", "SRL",
            "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique", "0123.456.789", "RPM Bruxelles",
            "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00", "atelier@bielle-bleue.test");

    private final CatalogueService catalogue = mock(CatalogueService.class);
    private MockMvc mvc;

    private final ArticleVue vidange = prestation("Vidange", "FORFAIT");
    private final ArticleVue diagnostic = prestation("Diagnostic", "HEURE");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ApiPubliqueController(catalogue, GARAGE)).build();
        when(catalogue.prestationsActives(any())).thenReturn(List.of(vidange, diagnostic));
    }

    private static ArticleVue prestation(String libelle, String unite) {
        return new ArticleVue(UUID.randomUUID(), "P-" + libelle, libelle, null, "Entretien",
                new BigDecimal("50.00"), new BigDecimal("60.50"), 30, null, true,
                null, null, null, unite, null, null, null);
    }

    @Test
    @DisplayName("collection : éléments sous _embedded, navigation sous _links, hal+json par défaut")
    void collection() throws Exception {
        mvc.perform(get("/api/v1/prestations").param("taille", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.first.href").exists())
                .andExpect(jsonPath("$._links.next.href", endsWith("page=1&taille=1")))
                .andExpect(jsonPath("$._links.prev").doesNotExist())
                .andExpect(jsonPath("$._embedded.prestations.length()").value(1))
                .andExpect(jsonPath("$.page.numero").value(0))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.nombreDePages").value(2));
    }

    @Test
    @DisplayName("chaque prestation porte ses liens, sans réservation pour un travail à l'heure")
    void liensDUnePrestation() throws Exception {
        mvc.perform(get("/api/v1/prestations"))
                .andExpect(jsonPath("$._embedded.prestations[0]._links.self.href",
                        endsWith("/api/v1/prestations/" + vidange.reference())))
                .andExpect(jsonPath("$._embedded.prestations[0]._links.garage.href",
                        endsWith("/api/v1/garages/0123456789")))
                .andExpect(jsonPath("$._embedded.prestations[0]._links.fiche.type").value("text/html"))
                .andExpect(jsonPath("$._embedded.prestations[0]._links.reservation.href",
                        endsWith("/mes-rendez-vous/nouveau?prestation=" + vidange.reference())))
                .andExpect(jsonPath("$._embedded.prestations[0]._links.self.type").doesNotExist())
                .andExpect(jsonPath("$._embedded.prestations[1]._links.reservation").doesNotExist());
    }

    @Test
    @DisplayName("une prestation seule, et 404 pour une référence que le service ne connaît pas")
    void ressourceUnitaire() throws Exception {
        UUID inconnue = UUID.randomUUID();
        when(catalogue.prestationActive(eq(vidange.reference()), any())).thenReturn(vidange);
        when(catalogue.prestationActive(eq(inconnue), any()))
                .thenThrow(new RessourceIntrouvableException("Prestation", inconnue));

        mvc.perform(get("/api/v1/prestations/{reference}", vidange.reference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Vidange"))
                .andExpect(jsonPath("$._links.collection.href", endsWith("/api/v1/prestations")));
        mvc.perform(get("/api/v1/prestations/{reference}", inconnue))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("le garage, point d'entrée : identifiant au numéro d'entreprise et liens vers le catalogue")
    void garage() throws Exception {
        mvc.perform(get("/api/v1/garages"))
                .andExpect(jsonPath("$._embedded.garages[0].identifiant").value("0123456789"))
                .andExpect(jsonPath("$._embedded.garages[0]._links.prestations.href", endsWith("/api/v1/prestations")))
                .andExpect(jsonPath("$._embedded.garages[0]._links.articles.href", endsWith("/api/v1/articles")))
                .andExpect(jsonPath("$._embedded.garages[0]._links.contact.type").value("text/html"));

        mvc.perform(get("/api/v1/garages/0123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href", endsWith("/api/v1/garages/0123456789")));
        mvc.perform(get("/api/v1/garages/9999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("application/json sur demande expresse, même corps")
    void jsonSurDemande() throws Exception {
        mvc.perform(get("/api/v1/garages").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.garages[0].identifiant").value("0123456789"));
    }
}
