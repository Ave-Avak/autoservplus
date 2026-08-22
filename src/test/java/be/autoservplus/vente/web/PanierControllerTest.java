package be.autoservplus.vente.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PieceInactiveException;
import be.autoservplus.vente.service.StockInsuffisantException;
import be.autoservplus.vente.web.dto.PanierVue;
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
 * WebMvcTest du controleur panier : delegation au service, pattern PRG avec flash,
 * traduction des refus metier en messages, CSRF, et 404 non catche sur une ligne
 * d autrui. Le rendu reel des templates est couvert par {@code PanierTemplatesIT}.
 */
@WebMvcTest(controllers = PanierController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({PanierControllerTest.SecuriteTest.class, PanierControllerTest.StubViewResolver.class})
@DisplayName("PanierController")
class PanierControllerTest {

    private static final UUID REF_PIECE = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired private MockMvc mvc;
    @MockitoBean private PanierService service;

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain filtresTest(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
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

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET /panier rend la vue avec le panier du membre connecte")
    void vueDuPanier() throws Exception {
        doReturn(PanierVue.vide()).when(service).panierDuMembre("marie@exemple.be");

        mvc.perform(get("/panier"))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/panier"))
                .andExpect(model().attributeExists("panier"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST /ajouter delegue au service avec l'identite du contexte, puis PRG")
    void ajoutDelegue() throws Exception {
        mvc.perform(post("/panier/ajouter").with(csrf())
                        .param("reference", REF_PIECE.toString())
                        .param("quantite", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("message"));

        verify(service).ajouterPiece("marie@exemple.be", REF_PIECE, 2);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("stock insuffisant : message d'erreur avec la quantite disponible, pas une 500")
    void stockInsuffisantEnMessage() throws Exception {
        doThrow(new StockInsuffisantException("Plaquettes", 5, 2))
                .when(service).ajouterPiece("marie@exemple.be", REF_PIECE, 5);

        mvc.perform(post("/panier/ajouter").with(csrf())
                        .param("reference", REF_PIECE.toString())
                        .param("quantite", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("piece inactive (RM-28) : message d'erreur, pas une 500")
    void pieceInactiveEnMessage() throws Exception {
        doThrow(new PieceInactiveException("Plaquettes"))
                .when(service).ajouterPiece("marie@exemple.be", REF_PIECE, 1);

        mvc.perform(post("/panier/ajouter").with(csrf())
                        .param("reference", REF_PIECE.toString())
                        .param("quantite", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("quantite invalide refusee par le service : consigne en flash")
    void quantiteInvalideEnMessage() throws Exception {
        doThrow(new IllegalArgumentException("La quantite doit valoir au moins 1."))
                .when(service).ajouterPiece("marie@exemple.be", REF_PIECE, 0);

        mvc.perform(post("/panier/ajouter").with(csrf())
                        .param("reference", REF_PIECE.toString())
                        .param("quantite", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST quantite d'une ligne delegue au service")
    void modificationDeleguee() throws Exception {
        mvc.perform(post("/panier/lignes/{id}/quantite", 7L).with(csrf())
                        .param("quantite", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("message"));

        verify(service).modifierQuantite("marie@exemple.be", 7L, 3);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("ligne d'autrui : RessourceIntrouvable remonte en 404, pas en flash")
    void ligneDAutruiEn404() throws Exception {
        doThrow(new RessourceIntrouvableException("LignePanier", 7L))
                .when(service).modifierQuantite("marie@exemple.be", 7L, 3);

        mvc.perform(post("/panier/lignes/{id}/quantite", 7L).with(csrf())
                        .param("quantite", "3"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST retirer delegue au service")
    void retraitDelegue() throws Exception {
        mvc.perform(post("/panier/lignes/{id}/retirer", 7L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));

        verify(service).retirerLigne("marie@exemple.be", 7L);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("vider passe par une confirmation : GET affiche l'ecran, POST execute")
    void viderAvecConfirmation() throws Exception {
        mvc.perform(get("/panier/vider"))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/vider"));

        mvc.perform(post("/panier/vider").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("message"));

        verify(service).vider("marie@exemple.be");
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST sans jeton CSRF est rejete")
    void postSansCsrfRejete() throws Exception {
        mvc.perform(post("/panier/ajouter")
                        .param("reference", REF_PIECE.toString())
                        .param("quantite", "1"))
                .andExpect(status().isForbidden());
    }
}
