package be.autoservplus.vente.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.service.PaiementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint webhook : accessible sans session ni jeton CSRF (configuration miroir
 * de SecuriteConfig), il delegue au service qui relit le statut aupres du
 * prestataire — le corps de la requete n est jamais cru.
 */
@WebMvcTest(controllers = PaiementWebhookController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import(PaiementWebhookControllerTest.SecuriteTest.class)
@DisplayName("PaiementWebhookController")
class PaiementWebhookControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PaiementService service;

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain filtresTest(HttpSecurity http) throws Exception {
            // Miroir de la configuration reelle : /webhooks/** public et hors CSRF.
            return http
                    .authorizeHttpRequests(a -> a
                            .requestMatchers("/webhooks/**").permitAll()
                            .anyRequest().authenticated())
                    .csrf(c -> c.ignoringRequestMatchers("/webhooks/**"))
                    .build();
        }
    }

    @Test
    @DisplayName("POST anonyme sans CSRF : 200, l'identifiant est delegue au service")
    void notificationAnonymeAcceptee() throws Exception {
        mvc.perform(post("/webhooks/paiement").param("id", "tr_fictif_0001"))
                .andExpect(status().isOk());

        verify(service).traiterNotification("tr_fictif_0001");
    }

    @Test
    @DisplayName("reference inconnue : 404 — un identifiant forge ne revele rien")
    void referenceInconnue() throws Exception {
        doThrow(new RessourceIntrouvableException("Paiement", "tr_forge"))
                .when(service).traiterNotification("tr_forge");

        mvc.perform(post("/webhooks/paiement").param("id", "tr_forge"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("parametre id manquant : 400")
    void parametreManquant() throws Exception {
        mvc.perform(post("/webhooks/paiement"))
                .andExpect(status().isBadRequest());
    }
}
