package be.autoservplus.common.web;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Statut des références inconnues ou mal formées.
 *
 * <p>MockMvc s'arrête au {@code sendError} et ne repasse pas par {@code /error} : ce test
 * ne vérifie que le statut. La page rendue, sa langue, son sélecteur et l'absence de
 * détail technique sont vérifiés sur un vrai serveur, par {@code PagesErreurIT} et
 * {@code ErreursSansDetailTechniqueIT}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Gestionnaire d'erreurs global (intégration)")
class GestionnaireErreursGlobalIT extends SocleIntegration {

    @Autowired private MockMvc mvc;

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("un rendez-vous inconnu rend 404, jamais 500")
    void rdvInconnu() throws Exception {
        mvc.perform(get("/mes-rendez-vous/{ref}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("un véhicule inconnu rend 404")
    void vehiculeInconnu() throws Exception {
        mvc.perform(get("/mes-vehicules/{ref}/modifier", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("une commande inconnue rend 404")
    void commandeInconnue() throws Exception {
        mvc.perform(get("/commandes/{ref}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("une référence mal formée dans le chemin rend 404, comme une référence inconnue")
    void referenceMalFormee() throws Exception {
        mvc.perform(get("/commandes/pas-un-uuid")).andExpect(status().isNotFound());
        mvc.perform(get("/mes-rendez-vous/12345")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("au back-office aussi, une référence mal formée rend 404")
    void referenceMalFormeeBackOffice() throws Exception {
        mvc.perform(get("/admin/interventions/pas-un-uuid")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/commandes/pas-un-uuid")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un paramètre de requête mal formé reste une demande mal formée : 400")
    void parametreMalForme() throws Exception {
        mvc.perform(get("/recherche").param("q", "filtre").param("pagePieces", "pas-un-nombre")).andExpect(status().isBadRequest());
    }
}
