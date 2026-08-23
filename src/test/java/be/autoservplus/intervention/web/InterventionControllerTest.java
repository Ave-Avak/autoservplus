package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.DemandeValidationVue;
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
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
    // BL-4 : le controleur demande a AvisService si le lien de depot doit apparaitre.
    @MockitoBean private be.autoservplus.avis.service.AvisService avis;

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
                "EN_COURS", "En cours",
                "VW Golf (1-ABC-123)",
                "Piece commandee, livraison mardi",
                "Lundi 14 septembre 2026 09:00", null,
                List.of(), "59,29 €", List.of(), false, false);
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

    // --- RM-15 : ecran de validation du depassement -----------------------------------

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET /validation rend l'ecran de decision pour le proprietaire")
    void ecranValidation() throws Exception {
        DemandeValidationVue demande = new DemandeValidationVue(REF, "INT-2026-0001",
                "VW Golf (1-ABC-123)", "49,00 €", "138,00 €", "89,00 €", null,
                List.of(new DemandeValidationVue.LigneProposeeVue("Plaquettes", (short) 1, "89,00 €")));
        doReturn(demande).when(service).demandeValidation(REF, "marie@exemple.be");

        mvc.perform(get("/mes-interventions/{ref}/validation", REF))
                .andExpect(status().isOk())
                .andExpect(view().name("intervention/validation"))
                .andExpect(model().attribute("demande", demande));
    }

    @Test
    @WithMockUser(username = "intrus@exemple.be")
    @DisplayName("GET /validation d'un autre membre -> 404")
    void ecranValidationOwnership() throws Exception {
        doThrow(new RessourceIntrouvableException("Intervention", REF))
                .when(service).demandeValidation(REF, "intrus@exemple.be");

        mvc.perform(get("/mes-interventions/{ref}/validation", REF))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST /valider delegue au service et redirige vers le suivi")
    void postValider() throws Exception {
        mvc.perform(post("/mes-interventions/{ref}/valider", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mes-interventions/" + REF))
                .andExpect(flash().attributeExists("message"));

        verify(service).validerDepassement(REF, "marie@exemple.be");
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST /refuser delegue au service et redirige vers le suivi")
    void postRefuser() throws Exception {
        mvc.perform(post("/mes-interventions/{ref}/refuser", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mes-interventions/" + REF))
                .andExpect(flash().attributeExists("message"));

        verify(service).refuserDepassement(REF, "marie@exemple.be");
    }

    @Test
    @WithMockUser(username = "intrus@exemple.be")
    @DisplayName("POST /valider sur l'intervention d'autrui -> 404, aucune ecriture")
    void postValiderOwnership() throws Exception {
        doThrow(new RessourceIntrouvableException("Intervention", REF))
                .when(service).validerDepassement(REF, "intrus@exemple.be");

        mvc.perform(post("/mes-interventions/{ref}/valider", REF).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "intrus@exemple.be")
    @DisplayName("POST /refuser sur l'intervention d'autrui -> 404, aucune ecriture")
    void postRefuserOwnership() throws Exception {
        doThrow(new RessourceIntrouvableException("Intervention", REF))
                .when(service).refuserDepassement(REF, "intrus@exemple.be");

        mvc.perform(post("/mes-interventions/{ref}/refuser", REF).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("double soumission : message d'erreur lisible, pas une 500")
    void doubleSoumission() throws Exception {
        doThrow(new IllegalStateException("Aucun depassement en attente"))
                .when(service).validerDepassement(REF, "marie@exemple.be");

        mvc.perform(post("/mes-interventions/{ref}/valider", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"));
    }

    @Test
    @DisplayName("POST sans jeton CSRF est rejete")
    @WithMockUser(username = "marie@exemple.be")
    void postSansCsrfRejete() throws Exception {
        mvc.perform(post("/mes-interventions/{ref}/valider", REF))
                .andExpect(status().isForbidden());
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
