package be.autoservplus.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Traitement global des erreurs (passe de consolidation).
 *
 * <p><b>Ce que ce test verrouille n est pas ce que la dette annoncait.</b> Le
 * registre disait « 500 brut sur reference absente » ; c etait perime —
 * {@code RessourceIntrouvableException} porte {@code @ResponseStatus(NOT_FOUND)}
 * depuis F32 et rendait deja 404. Le test fige donc ce 404 pour qu il ne
 * regresse pas, et verifie surtout les deux apports reels : une page rendue au
 * lieu du Whitelabel de Boot, et l absence de fuite technique.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Gestionnaire d erreurs global (integration)")
class GestionnaireErreursGlobalIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;

    @Nested
    @DisplayName("Reference absente")
    class ReferenceAbsente {

        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("un rendez-vous inconnu rend 404, jamais 500")
        void rdvInconnu() throws Exception {
            // RdvController.detail ne catche pas RessourceIntrouvableException :
            // c'est precisement le chemin que la dette signalait.
            mvc.perform(get("/mes-rendez-vous/{ref}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("erreur"));
        }

        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("un vehicule inconnu rend 404 par le meme chemin")
        void vehiculeInconnu() throws Exception {
            mvc.perform(get("/mes-vehicules/{ref}/modifier", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("erreur"));
        }

        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("une commande inconnue rend 404")
        void commandeInconnue() throws Exception {
            mvc.perform(get("/commandes/{ref}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("la page d erreur ne divulgue aucun detail technique")
        void aucuneFuiteTechnique() throws Exception {
            mvc.perform(get("/mes-rendez-vous/{ref}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    // Ni nom d'exception, ni trace, ni identifiant recherche : une
                    // page d'erreur qui recopie le message raconte la structure interne.
                    .andExpect(content().string(not(containsString("Exception"))))
                    .andExpect(content().string(not(containsString("be.autoservplus"))))
                    .andExpect(content().string(not(containsString("introuvable :"))));
        }

        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("la page d erreur est rendue par nous, pas par le Whitelabel de Boot")
        void pageMaison() throws Exception {
            mvc.perform(get("/mes-rendez-vous/{ref}", UUID.randomUUID()))
                    .andExpect(content().string(containsString("AutoServ+")))
                    .andExpect(content().string(not(containsString("Whitelabel"))));
        }
    }
}
