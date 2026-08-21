package be.autoservplus.reservation.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.service.AdminRdvService;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * Tests web du controleur admin.
 *
 * <p>Une config de securite dediee au test reproduit uniquement la regle d URL
 * {@code /admin/**} de {@code SecuriteConfig}, SANS {@code @EnableMethodSecurity} :
 * le {@code @PreAuthorize} du service reste en prod (defense en profondeur), mais
 * n est pas active ici pour eviter que l AOP wrappe le {@link MockitoBean} et
 * cause des appels vers le vrai code depuis les stubbings.</p>
 *
 * <p>Les templates admin n existent pas encore (arrivent au commit suivant).
 * {@code ThymeleafAutoConfiguration} est exclu du slice, et un {@link ViewResolver}
 * stub prend sa place : le test verifie le nom de vue et le contenu du modele,
 * pas le HTML rendu.</p>
 */
@WebMvcTest(controllers = AdminRdvController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({AdminRdvControllerTest.SecuriteTest.class, AdminRdvControllerTest.StubViewResolver.class})
@DisplayName("AdminRdvController")
class AdminRdvControllerTest {

    private static final UUID REF = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired private MockMvc mvc;
    @MockitoBean private AdminRdvService adminRdvs;

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
            // Sans Thymeleaf, ce ViewResolver est le seul de la chaine. Il renvoie
            // pour tout nom de vue une View no-op : le DispatcherServlet appelle
            // render() qui n ecrit rien, le test verifie modele et view name.
            return (viewName, locale) -> (model, request, response) -> { /* no-op */ };
        }
    }

    private RdvVueAdmin vueEnAttente;
    private Rdv rdvMock;

    @BeforeEach
    void setUp() {
        // Reset explicite : le mock adminRdvs est partage entre les @Nested au
        // sein du meme contexte Spring, et le reset automatique de @MockitoBean
        // peut ne pas nettoyer les invocations enregistrees si un stubbing via
        // when() a laisse une invocation « phantom ».
        org.mockito.Mockito.reset(adminRdvs);
        vueEnAttente = new RdvVueAdmin(REF, "RDV-2026-0001",
                "EN_ATTENTE", "En attente de confirmation",
                "dimanche 13 septembre 2026", "10:00", "10:30",
                "Marie Dupont", "marie@exemple.be",
                "VW Golf (1-ABC-123)",
                List.of("Vidange"),
                "49,00 €", null, null,
                true, true, true, false, false);
        rdvMock = mock(Rdv.class);
        when(rdvMock.getNumero()).thenReturn("RDV-2026-0001");
    }

    @Nested
    @DisplayName("liste")
    class Liste {

        @Test
        @DisplayName("expose demandesEnAttente et rendezVousATraiter dans le modele")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void afficheLesDeuxSections() throws Exception {
            when(adminRdvs.demandesEnAttente()).thenReturn(List.of(vueEnAttente));
            when(adminRdvs.rendezVousATraiter()).thenReturn(List.of());

            mvc.perform(get("/admin/rendez-vous"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/rendez-vous"))
                    .andExpect(model().attribute("demandesEnAttente", List.of(vueEnAttente)))
                    .andExpect(model().attribute("rendezVousATraiter", List.of()));
        }

        @Test
        @DisplayName("un membre sans role ADMINISTRATEUR obtient 403")
        @WithMockUser(roles = "MEMBRE")
        void interditAuxMembres() throws Exception {
            mvc.perform(get("/admin/rendez-vous"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("confirmer")
    class Confirmer {

        @Test
        @DisplayName("succes : redirect vers la liste avec un flash message")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void confirme() throws Exception {
            doReturn(rdvMock).when(adminRdvs).confirmer(REF);

            doReturn(rdvMock).when(adminRdvs).confirmer(REF);

            mvc.perform(post("/admin/rendez-vous/{ref}/confirmer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/rendez-vous"))
                    .andExpect(flash().attributeExists("message"));

            verify(adminRdvs).confirmer(REF);
        }

        @Test
        @DisplayName("conflit de concurrence : redirect avec flash erreur")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void conflitConcurrence() throws Exception {
            doThrow(new ConflitConcurrenceException("Ce rendez-vous a été mis à jour par un autre administrateur."))
                    .when(adminRdvs).confirmer(REF);

            mvc.perform(post("/admin/rendez-vous/{ref}/confirmer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/rendez-vous"))
                    .andExpect(flash().attributeExists("erreur"));
        }

        @Test
        @DisplayName("transition interdite (RM-10) : redirect avec flash erreur")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void transitionInterdite() throws Exception {
            doThrow(new IllegalStateException("Transition interdite : REFUSE vers CONFIRME (RM-10)."))
                    .when(adminRdvs).confirmer(REF);

            mvc.perform(post("/admin/rendez-vous/{ref}/confirmer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/rendez-vous"))
                    .andExpect(flash().attributeExists("erreur"));
        }
    }

    @Nested
    @DisplayName("refuser")
    class Refuser {

        @Test
        @DisplayName("GET affiche le formulaire motif avec la vue du RDV")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void afficheFormulaire() throws Exception {
            when(adminRdvs.vue(REF)).thenReturn(vueEnAttente);

            mvc.perform(get("/admin/rendez-vous/{ref}/refuser", REF))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/refuser"))
                    .andExpect(model().attributeExists("formulaire"))
                    .andExpect(model().attribute("rdv", vueEnAttente));
        }

        @Test
        @DisplayName("POST motif vide : re-render sans appel au service, avec FieldError")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void motifBlankReRender() throws Exception {
            when(adminRdvs.vue(REF)).thenReturn(vueEnAttente);

            mvc.perform(post("/admin/rendez-vous/{ref}/refuser", REF)
                            .param("motif", "   ")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/refuser"))
                    .andExpect(model().attributeHasFieldErrors("formulaire", "motif"));

            verify(adminRdvs, never()).refuser(any(), any());
        }

        @Test
        @DisplayName("POST motif valide : redirect + flash succes, service appele avec le motif")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void motifValideRefuse() throws Exception {
            when(adminRdvs.refuser(REF, "Piece indisponible")).thenReturn(rdvMock);

            mvc.perform(post("/admin/rendez-vous/{ref}/refuser", REF)
                            .param("motif", "Piece indisponible")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/rendez-vous"))
                    .andExpect(flash().attributeExists("message"));

            verify(adminRdvs).refuser(REF, "Piece indisponible");
        }
    }
}
