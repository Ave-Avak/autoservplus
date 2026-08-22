package be.autoservplus.rgpd.web;

import be.autoservplus.rgpd.service.ExportDonneesService;
import be.autoservplus.rgpd.service.ExportTropRecentException;
import be.autoservplus.rgpd.service.ReauthentificationEchoueeException;
import be.autoservplus.rgpd.service.dto.FichierExport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ViewResolver;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Ecran « Mes donnees personnelles » : delegation au service, en-tetes de
 * telechargement, traduction des refus, CSRF et protection d acces. Le rendu reel
 * du template est couvert par {@code ExportDonneesIT}.
 */
@WebMvcTest(controllers = DonneesPersonnellesController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({DonneesPersonnellesControllerTest.SecuriteTest.class,
        DonneesPersonnellesControllerTest.StubViewResolver.class})
@DisplayName("DonneesPersonnellesController")
class DonneesPersonnellesControllerTest {

    private static final String EMAIL = "marie@exemple.be";

    @Autowired private MockMvc mvc;
    @MockitoBean private ExportDonneesService service;

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
        Locale.setDefault(Locale.FRANCE);
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("GET /mes-donnees rend l'ecran et autorise l'export quand aucun n'est recent")
    void ecranExportPossible() throws Exception {
        doReturn(Optional.empty()).when(service).attenteRestante(EMAIL);

        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE))
                .andExpect(status().isOk())
                .andExpect(view().name("rgpd/mes-donnees"))
                .andExpect(model().attribute("exportPossible", true))
                .andExpect(model().attributeDoesNotExist("message"))
                .andExpect(model().attributeDoesNotExist("erreur"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("GET /mes-donnees confirme l'export precedent et annonce le delai restant")
    void ecranAvecExportRecent() throws Exception {
        doReturn(Optional.of(Duration.ofHours(3).plusMinutes(12)))
                .when(service).attenteRestante(EMAIL);

        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE))
                .andExpect(status().isOk())
                .andExpect(model().attribute("exportPossible", false))
                .andExpect(model().attribute("message",
                        org.hamcrest.Matchers.containsString("3 h 12 min")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("POST /mes-donnees/export sert le fichier en piece jointe, nom date")
    void telechargement() throws Exception {
        byte[] contenu = "{\"donnees_personnelles\":{}}".getBytes(StandardCharsets.UTF_8);
        doReturn(new FichierExport("mes-donnees-2026-08-22.json", contenu))
                .when(service).exporter(EMAIL, "MotDePasseDeMarie!");

        mvc.perform(post("/mes-donnees/export").with(csrf())
                        .param("motDePasse", "MotDePasseDeMarie!"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("mes-donnees-2026-08-22.json")))
                .andExpect(content().bytes(contenu));

        // L'identite vient du contexte de securite, jamais d'un parametre.
        verify(service).exporter(EMAIL, "MotDePasseDeMarie!");
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("mauvais mot de passe : redirection codee, aucun corps servi")
    void refusMotDePasse() throws Exception {
        doThrow(new ReauthentificationEchoueeException())
                .when(service).exporter(EMAIL, "mauvais");

        mvc.perform(post("/mes-donnees/export").with(csrf()).param("motDePasse", "mauvais"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("http://localhost/mes-donnees?erreur=motdepasse"))
                .andExpect(content().string(""));
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("limite atteinte : redirection codee, puis message avec le delai restant")
    void refusLimite() throws Exception {
        doThrow(new ExportTropRecentException(Duration.ofHours(23).plusMinutes(5)))
                .when(service).exporter(EMAIL, "MotDePasseDeMarie!");

        mvc.perform(post("/mes-donnees/export").with(csrf())
                        .param("motDePasse", "MotDePasseDeMarie!"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("http://localhost/mes-donnees?erreur=limite"));

        // Le delai n'est pas transporte dans l'URL : il est recalcule a l'affichage.
        doReturn(Optional.of(Duration.ofHours(23).plusMinutes(5)))
                .when(service).attenteRestante(EMAIL);
        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE).param("erreur", "limite"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erreur",
                        org.hamcrest.Matchers.containsString("23 h 5 min")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("un code d'erreur inconnu dans l'URL n'affiche aucun message")
    void codeErreurInconnu() throws Exception {
        doReturn(Optional.empty()).when(service).attenteRestante(EMAIL);

        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE).param("erreur", "n-importe-quoi"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("erreur"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("le refus de quota n'est plus affiche si le delai a expire entre-temps")
    void limiteExpireeEntreTemps() throws Exception {
        doReturn(Optional.empty()).when(service).attenteRestante(EMAIL);

        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE).param("erreur", "limite"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("erreur"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("l'export sans jeton CSRF est refuse")
    void csrfObligatoire() throws Exception {
        mvc.perform(post("/mes-donnees/export").param("motDePasse", "MotDePasseDeMarie!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("un visiteur anonyme n'atteint pas l'ecran")
    void accesAnonymeRefuse() throws Exception {
        mvc.perform(get("/mes-donnees"))
                .andExpect(status().is4xxClientError());
    }
}
