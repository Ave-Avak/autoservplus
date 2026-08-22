package be.autoservplus.intervention.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * WebMvcTest du controleur admin des interventions. Meme structure que
 * {@code AdminRdvControllerTest} : securite d URL test-only sans
 * {@code @EnableMethodSecurity} (l AOP interfere avec @MockitoBean), stub
 * ViewResolver pour ne pas dependre des templates a ce niveau, doReturn/doThrow
 * pour eviter les invocations phantom.
 */
@WebMvcTest(controllers = AdminInterventionController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({AdminInterventionControllerTest.SecuriteTest.class, AdminInterventionControllerTest.StubViewResolver.class})
@DisplayName("AdminInterventionController")
class AdminInterventionControllerTest {

    private static final UUID REF = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired private MockMvc mvc;
    @MockitoBean private InterventionService service;

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain filtresTest(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(a -> a
                            .requestMatchers("/admin/**").hasRole("ADMINISTRATEUR")
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

    private InterventionVueAdmin vueDemo;
    private Intervention interventionMock;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        vueDemo = new InterventionVueAdmin(REF, "INT-2026-0001",
                "PLANIFIEE", "Planifiée",
                "RDV-2026-0001",
                "VW Golf (1-ABC-123)",
                "Marie Dupont", "marie@exemple.be",
                null, null, null,
                List.of(),
                "49,00 €", "59,29 €",
                true, false, false, false, false, true,
                false, "49,00 €", "49,00 €");
        interventionMock = mock(Intervention.class);
        when(interventionMock.getNumero()).thenReturn("INT-2026-0001");
    }

    @Nested
    @DisplayName("liste")
    class Liste {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("expose les interventions en cours dans le modele")
        void afficheListe() throws Exception {
            when(service.interventionsEnCours()).thenReturn(List.of(vueDemo));

            mvc.perform(get("/admin/interventions"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/interventions"))
                    .andExpect(model().attribute("interventions", List.of(vueDemo)));
        }

        @Test
        @WithMockUser(roles = "MEMBRE")
        @DisplayName("un membre sans role ADMINISTRATEUR obtient 403")
        void interditAuxMembres() throws Exception {
            mvc.perform(get("/admin/interventions"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("transitions")
    class Transitions {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("demarrer redirige avec flash message et appelle le service")
        void demarrer() throws Exception {
            doReturn(interventionMock).when(service).demarrer(REF);

            mvc.perform(post("/admin/interventions/{ref}/demarrer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/interventions/" + REF))
                    .andExpect(flash().attributeExists("message"));

            verify(service).demarrer(REF);
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("conflit concurrence -> flash erreur")
        void conflit() throws Exception {
            doThrow(new ConflitConcurrenceException("stale"))
                    .when(service).demarrer(REF);

            mvc.perform(post("/admin/interventions/{ref}/demarrer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("erreur"));
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("transition interdite (IllegalState) -> flash erreur")
        void transitionInterdite() throws Exception {
            doThrow(new IllegalStateException("Transition d intervention interdite : PLANIFIEE vers TERMINEE."))
                    .when(service).terminer(REF);

            mvc.perform(post("/admin/interventions/{ref}/terminer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("erreur"));
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("suspendre redirige avec flash message et appelle le service")
        void suspendre() throws Exception {
            doReturn(interventionMock).when(service).suspendre(REF);

            mvc.perform(post("/admin/interventions/{ref}/suspendre", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/interventions/" + REF))
                    .andExpect(flash().attributeExists("message"));

            verify(service).suspendre(REF);
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("annuler redirige avec flash message et appelle le service")
        void annuler() throws Exception {
            doReturn(interventionMock).when(service).annuler(REF);

            mvc.perform(post("/admin/interventions/{ref}/annuler", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/interventions/" + REF))
                    .andExpect(flash().attributeExists("message"));

            verify(service).annuler(REF);
        }
    }

    @Nested
    @DisplayName("commentaire et lignes")
    class Edition {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("POST commentaire appelle le service et redirige")
        void modifierCommentaire() throws Exception {
            doReturn(interventionMock).when(service).modifierCommentaireAdmin(REF, "Piece commandee");

            mvc.perform(post("/admin/interventions/{ref}/commentaire", REF)
                            .param("commentaire", "Piece commandee")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("message"));

            verify(service).modifierCommentaireAdmin(REF, "Piece commandee");
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("POST ajouter ligne appelle le service avec les parametres du formulaire")
        void ajouterLigne() throws Exception {
            UUID prestation = UUID.fromString("33333333-3333-3333-3333-333333333333");

            mvc.perform(post("/admin/interventions/{ref}/lignes", REF)
                            .param("prestation", prestation.toString())
                            .param("quantite", "2")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());

            verify(service).ajouterLigneMainOeuvre(REF, prestation, (short) 2);
        }
    }
}
