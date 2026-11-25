package be.autoservplus.config;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Adresses ouvertes à l'anonyme par {@code SecuriteConfig} pour le catalogue en rayons,
 * l'API des articles, le plan du site et {@code robots.txt}. Un anonyme n'y est jamais
 * renvoyé à la connexion. Le contenu de chaque page est vérifié ailleurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithAnonymousUser
@DisplayName("Adresses publiques du catalogue et du plan du site (intégration)")
class AdressesPubliquesIT extends SocleIntegration {

    @Autowired private MockMvc mvc;

    @ParameterizedTest(name = "{0} est ouverte à l'anonyme")
    @ValueSource(strings = {"/catalogue", "/accessoires", "/entretien", "/accessoires/inconnue",
            "/entretien/inconnue", "/api/v1/articles", "/sitemap.xml", "/robots.txt"})
    void ouverte(String adresse) throws Exception {
        MockHttpServletResponse reponse = mvc.perform(get(adresse)).andReturn().getResponse();

        assertThat(String.valueOf(reponse.getRedirectedUrl())).doesNotEndWith("/connexion");
        assertThat(reponse.getStatus()).isNotEqualTo(401).isNotEqualTo(403);
    }

    @Test
    @DisplayName("l'API reste énumérée : une autre route /api/v1 renvoie toujours à la connexion")
    void apiToujoursEnumeree() throws Exception {
        MockHttpServletResponse reponse = mvc.perform(get("/api/v1/articles-prives")).andReturn().getResponse();

        assertThat(reponse.getRedirectedUrl()).endsWith("/connexion");
    }
}
