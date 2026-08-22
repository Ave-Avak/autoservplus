package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.DoublonCatalogueException;
import be.autoservplus.catalogue.service.SuppressionRefuseeException;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
 * WebMvcTest du controleur admin des pieces, symetrique d
 * {@code AdminPrestationControllerTest} : seuls les comportements propres aux
 * pieces (reference fabricant, stock) et le tronc commun critique sont testes,
 * la symetrie du reste est portee par le meme code de base.
 */
@WebMvcTest(controllers = AdminPieceController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({AdminPieceControllerTest.SecuriteTest.class, AdminPieceControllerTest.StubViewResolver.class})
@DisplayName("AdminPieceController")
class AdminPieceControllerTest {

    private static final UUID REF = UUID.fromString("44444444-4444-4444-4444-444444444444");

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

    private ArticleVueAdmin vueFiltre;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        vueFiltre = new ArticleVueAdmin(REF, "F-001", "Filtre à huile", null,
                "P_FILTRES", "Filtres", new BigDecimal("12.50"), new BigDecimal("15.13"),
                new BigDecimal("21.00"), null, "Bosch", 8, 2, true);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("la liste expose le catalogue complet des pieces")
    void afficheListe() throws Exception {
        doReturn(List.of(vueFiltre)).when(service).piecesPourAdmin();

        mvc.perform(get("/admin/catalogue/pieces"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/catalogue/pieces"))
                .andExpect(model().attribute("pieces", List.of(vueFiltre)));
    }

    @Test
    @WithMockUser(roles = "MEMBRE")
    @DisplayName("un membre sans role ADMINISTRATEUR obtient 403")
    void interditAuxMembres() throws Exception {
        mvc.perform(get("/admin/catalogue/pieces"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("A4 : une creation valide (avec stock initial) delegue au service puis PRG")
    void creationValide() throws Exception {
        mvc.perform(post("/admin/catalogue/pieces").with(csrf())
                        .param("codeCategorie", "P_FILTRES")
                        .param("referenceFabricant", "F-001")
                        .param("libelle", "Filtre à huile")
                        .param("marque", "Bosch")
                        .param("prixHtva", "12.50")
                        .param("tauxTva", "21.00")
                        .param("quantiteStock", "8")
                        .param("seuilAlerte", "2")
                        .param("actif", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/catalogue/pieces"))
                .andExpect(flash().attributeExists("message"));

        verify(service).creerPiece(any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("A4 : une reference fabricant deja prise est raccrochee au champ")
    void referenceDupliquee() throws Exception {
        doThrow(new DoublonCatalogueException("referenceFabricant", "F-001", "deja enregistree"))
                .when(service).creerPiece(any());

        mvc.perform(post("/admin/catalogue/pieces").with(csrf())
                        .param("codeCategorie", "P_FILTRES")
                        .param("referenceFabricant", "F-001")
                        .param("libelle", "Filtre à huile")
                        .param("prixHtva", "12.50")
                        .param("tauxTva", "21.00")
                        .param("quantiteStock", "8")
                        .param("seuilAlerte", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/catalogue/piece-formulaire"))
                .andExpect(model().attributeHasFieldErrors("formulaire", "referenceFabricant"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("la validation serveur refuse un stock negatif sans appeler le service")
    void stockNegatifRefuse() throws Exception {
        mvc.perform(post("/admin/catalogue/pieces").with(csrf())
                        .param("codeCategorie", "P_FILTRES")
                        .param("referenceFabricant", "F-001")
                        .param("libelle", "Filtre à huile")
                        .param("prixHtva", "12.50")
                        .param("tauxTva", "21.00")
                        .param("quantiteStock", "-1")
                        .param("seuilAlerte", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/catalogue/piece-formulaire"))
                .andExpect(model().attributeHasFieldErrors("formulaire", "quantiteStock"));

        verify(service, never()).creerPiece(any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("la page de confirmation expose le diagnostic RM-29")
    void afficheDiagnostic() throws Exception {
        PropositionSuppression proposition =
                new PropositionSuppression(REF, "F-001", "Filtre à huile", 1, true);
        doReturn(proposition).when(service).propositionSuppressionPiece(REF);

        mvc.perform(get("/admin/catalogue/pieces/{ref}/supprimer", REF))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/catalogue/piece-supprimer"))
                .andExpect(model().attribute("proposition", proposition));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("RM-29 : un refus de suppression devient un flash d erreur")
    void suppressionRefusee() throws Exception {
        doReturn(new PropositionSuppression(REF, "F-001", "Filtre à huile", 1, true))
                .when(service).propositionSuppressionPiece(REF);
        doThrow(new SuppressionRefuseeException("Filtre à huile", 1))
                .when(service).supprimerDefinitivementPiece(REF);

        mvc.perform(post("/admin/catalogue/pieces/{ref}/supprimer", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"));
    }
}
