package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.DoublonCatalogueException;
import be.autoservplus.catalogue.service.SuppressionRefuseeException;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
 * WebMvcTest du controleur admin des prestations. Meme structure que les autres
 * slices web : securite d URL test-only, stub ViewResolver, doReturn/doThrow.
 * Le rendu reel des templates et la securite de bout en bout sont couverts par
 * {@code AdminCatalogueTemplatesIT}.
 */
@WebMvcTest(controllers = AdminPrestationController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({AdminPrestationControllerTest.SecuriteTest.class, AdminPrestationControllerTest.StubViewResolver.class})
@DisplayName("AdminPrestationController")
class AdminPrestationControllerTest {

    private static final UUID REF = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired private MockMvc mvc;
    @MockitoBean private AdminCatalogueService service;

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

    private ArticleVueAdmin vueVidange;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        vueVidange = new ArticleVueAdmin(REF, "VID", "Vidange", "Huile et filtre",
                "ENTRETIEN", "Entretien", new BigDecimal("75.00"), new BigDecimal("90.75"),
                new BigDecimal("21.00"), 60, null, null, null, true);
    }

    @Nested
    @DisplayName("liste")
    class Liste {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("expose le catalogue complet dans le modele")
        void afficheListe() throws Exception {
            doReturn(List.of(vueVidange)).when(service).prestationsPourAdmin();

            mvc.perform(get("/admin/catalogue/prestations"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/catalogue/prestations"))
                    .andExpect(model().attribute("prestations", List.of(vueVidange)));
        }

        @Test
        @WithMockUser(roles = "MEMBRE")
        @DisplayName("un membre sans role ADMINISTRATEUR obtient 403")
        void interditAuxMembres() throws Exception {
            mvc.perform(get("/admin/catalogue/prestations"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("creation (A1)")
    class Creation {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("le formulaire vierge propose categories et taux belges")
        void afficheFormulaire() throws Exception {
            mvc.perform(get("/admin/catalogue/prestations/nouvelle"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/catalogue/prestation-formulaire"))
                    .andExpect(model().attributeExists("formulaire", "categories", "tauxAdmis"))
                    .andExpect(model().attribute("edition", false));
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("une soumission valide delegue au service puis PRG avec flash")
        void creationValide() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "ENTRETIEN")
                            .param("code", "VID")
                            .param("libelle", "Vidange")
                            .param("prixHtva", "75.00")
                            .param("tauxTva", "21.00")
                            .param("dureeMinutes", "60")
                            .param("actif", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/catalogue/prestations"))
                    .andExpect(flash().attributeExists("message"));

            verify(service).creerPrestation(any());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("la validation serveur bloque un nom vide sans appeler le service")
        void nomObligatoire() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "ENTRETIEN")
                            .param("code", "VID")
                            .param("libelle", "")
                            .param("prixHtva", "75.00")
                            .param("tauxTva", "21.00")
                            .param("dureeMinutes", "60"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/catalogue/prestation-formulaire"))
                    .andExpect(model().attributeHasFieldErrors("formulaire", "libelle"));

            verify(service, never()).creerPrestation(any());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("A1 : un nom deja pris est raccroche au champ du formulaire")
        void nomDuplique() throws Exception {
            doThrow(new DoublonCatalogueException("libelle", "Vidange", "existe deja"))
                    .when(service).creerPrestation(any());

            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "ENTRETIEN")
                            .param("code", "VID2")
                            .param("libelle", "Vidange")
                            .param("prixHtva", "75.00")
                            .param("tauxTva", "21.00")
                            .param("dureeMinutes", "60"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/catalogue/prestation-formulaire"))
                    .andExpect(model().attributeHasFieldErrors("formulaire", "libelle"));
        }

        @Test
        @DisplayName("POST sans jeton CSRF est rejete")
        @WithMockUser(roles = "ADMINISTRATEUR")
        void csrfRequis() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations")
                            .param("libelle", "Vidange"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("modification (A2)")
    class Modification {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("le formulaire d edition est pre-rempli depuis la vue admin")
        void afficheEdition() throws Exception {
            doReturn(vueVidange).when(service).vuePrestation(REF);

            mvc.perform(get("/admin/catalogue/prestations/{ref}/modifier", REF))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/catalogue/prestation-formulaire"))
                    .andExpect(model().attribute("edition", true))
                    .andExpect(model().attribute("reference", REF));
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("une soumission valide delegue au service puis PRG avec flash")
        void modificationValide() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations/{ref}/modifier", REF).with(csrf())
                            .param("codeCategorie", "ENTRETIEN")
                            .param("code", "VID")
                            .param("libelle", "Vidange longue durée")
                            .param("prixHtva", "89.00")
                            .param("tauxTva", "21.00")
                            .param("dureeMinutes", "45"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/catalogue/prestations"))
                    .andExpect(flash().attributeExists("message"));

            verify(service).modifierPrestation(eq(REF), any());
        }
    }

    @Nested
    @DisplayName("retrait (A3, RM-29)")
    class Retrait {

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("la page de confirmation expose le diagnostic RM-29")
        void afficheDiagnostic() throws Exception {
            PropositionSuppression proposition =
                    new PropositionSuppression(REF, "VID", "Vidange", 2, true);
            doReturn(proposition).when(service).propositionSuppressionPrestation(REF);

            mvc.perform(get("/admin/catalogue/prestations/{ref}/supprimer", REF))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/catalogue/prestation-supprimer"))
                    .andExpect(model().attribute("proposition", proposition));
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("la suppression aboutit avec flash de succes")
        void suppressionAboutit() throws Exception {
            doReturn(new PropositionSuppression(REF, "VID", "Vidange", 0, true))
                    .when(service).propositionSuppressionPrestation(REF);

            mvc.perform(post("/admin/catalogue/prestations/{ref}/supprimer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("message"));

            verify(service).supprimerDefinitivementPrestation(REF);
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("RM-29 : un refus du service devient un flash d erreur")
        void suppressionRefusee() throws Exception {
            doReturn(new PropositionSuppression(REF, "VID", "Vidange", 2, true))
                    .when(service).propositionSuppressionPrestation(REF);
            doThrow(new SuppressionRefuseeException("Vidange", 2))
                    .when(service).supprimerDefinitivementPrestation(REF);

            mvc.perform(post("/admin/catalogue/prestations/{ref}/supprimer", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("erreur"));
        }

        @Test
        @WithMockUser(roles = "ADMINISTRATEUR")
        @DisplayName("RM-28 : la desactivation delegue au service avec flash de succes")
        void desactivation() throws Exception {
            doReturn(vueVidange).when(service).vuePrestation(REF);

            mvc.perform(post("/admin/catalogue/prestations/{ref}/desactiver", REF).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("message"));

            verify(service).desactiverPrestation(REF);
        }
    }
}
