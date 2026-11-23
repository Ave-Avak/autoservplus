package be.autoservplus.config;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le réglage du profil prod, posé ici par propriété : la chaîne de sécurité ne remplace pas
 * l'en-tête de cache qu'une ressource statique porte déjà. Contexte propre, la propriété
 * changeant la configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.web.resources.cache.cachecontrol.max-age=1h")
@DisplayName("Cache des ressources statiques (intégration)")
class CacheDesStatiquesIT extends SocleIntegration {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("la feuille de style et une notice de garantie se gardent une heure")
    void statiquesEnCache() throws Exception {
        mvc.perform(get("/css/autoservplus.css").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600"));
        mvc.perform(get("/images/notice-garantie/notice-garantie-fr.svg").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600"));
    }

    @Test
    @DisplayName("une page reste hors de tout cache")
    void pagesSansCache() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
