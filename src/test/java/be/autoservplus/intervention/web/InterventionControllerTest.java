package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ViewResolver;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * WebMvcTest du controleur membre. Verifie ownership (404 si non-proprietaire,
 * via {@code @ResponseStatus(NOT_FOUND)} sur RessourceIntrouvableException),
 * rendu du fragment blocStatut isole, et redirection depuis-rdv.
 */
@WebMvcTest(controllers = InterventionController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({InterventionControllerTest.SecuriteTest.class, InterventionControllerTest.StubViewResolver.class})
@DisplayName("InterventionController (membre)")
class InterventionControllerTest {

    private static final UUID REF = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired private MockMvc mvc;
    @MockitoBean private InterventionService service;

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain filtresTest(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(a -> a
                            .requestMatchers("/mes-interventions/**").authenticated()
                            .anyRequest().authenticated())
                    .csrf(Customizer.withDefaults())
                    .build();
        }
    }

    @TestConfiguration
    static class StubViewResolver {
        @Bean
        ViewResolver stubViewResolver() {
            return (viewName, locale) -> (model, request, response) -> { /* no-op */ };
        }
    }

    private InterventionVueMembre vueDemo;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        vueDemo = new InterventionVueMembre(REF, "INT-2026-0001",
                "EN_COURS", "En cours au garage",
                "VW Golf (1-ABC-123)",
                "Piece commandee, livraison mardi",
                "Lundi 14 septembre 2026 09:00", null,
                List.of(), "59,29 €", false);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET suivi renvoie la vue complete pour le proprietaire")
    void suiviProprietaire() throws Exception {
        doReturn(vueDemo).when(service).interventionDuMembre(REF, "marie@exemple.be");

        mvc.perform(get("/mes-interventions/{ref}", REF))
                .andExpect(status().isOk())
                .andExpect(view().name("intervention/suivi"))
                .andExpect(model().attribute("intervention", vueDemo));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("intervention d'un autre membre -> 404 (RessourceIntrouvable + @ResponseStatus)")
    void ownershipRefuse() throws Exception {
        doThrow(new RessourceIntrouvableException("Intervention", REF))
                .when(service).interventionDuMembre(REF, "marie@exemple.be");

        mvc.perform(get("/mes-interventions/{ref}", REF))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET /statut renvoie le fragment blocStatut isole (nom de vue avec ::)")
    void fragmentStatut() throws Exception {
        doReturn(vueDemo).when(service).interventionDuMembre(REF, "marie@exemple.be");

        mvc.perform(get("/mes-interventions/{ref}/statut", REF))
                .andExpect(status().isOk())
                .andExpect(view().name("intervention/suivi :: blocStatut"))
                .andExpect(model().attribute("intervention", vueDemo));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET /depuis-rdv redirige vers l'intervention correspondante")
    void depuisRdvRedirige() throws Exception {
        UUID rdvRef = UUID.fromString("55555555-5555-5555-5555-555555555555");
        doReturn(REF).when(service).referenceParRdvDuMembre(rdvRef, "marie@exemple.be");

        mvc.perform(get("/mes-interventions/depuis-rdv/{ref}", rdvRef))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mes-interventions/" + REF));
    }
}
