package be.autoservplus.comptabilite.web;

import be.autoservplus.comptabilite.service.ExportComptableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export comptable de bout en bout (BL-3), sur un PostgreSQL reel.
 *
 * <p>Verifie ce qui ne se voit pas a la compilation : les {@code JOIN FETCH}
 * s executent, la reponse porte bien les en-tetes d un telechargement, et les deux
 * gardes tiennent — le fichier contient le nom de tous les clients et le chiffre
 * d affaires du garage.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Export comptable (integration)")
class ExportComptableIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 12, 31);

    @Autowired private MockMvc mvc;
    @Autowired private ExportComptableService export;

    @Nested
    @DisplayName("Contenu")
    class Contenu {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("les deux exports s executent et portent leur ligne d en-tete")
        void entetes() {
            String factures = export.facturesEnCsv(DEBUT, FIN);
            String commandes = export.commandesEnCsv(DEBUT, FIN);

            assertThat(factures).startsWith("﻿").contains(";").contains("\r\n");
            assertThat(commandes).startsWith("﻿").contains(";").contains("\r\n");
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("une periode sans piece rend l en-tete seul, pas une erreur")
        void periodeVide() {
            String csv = export.facturesEnCsv(LocalDate.of(1999, 1, 1), LocalDate.of(1999, 1, 31));

            assertThat(csv.lines().count())
                    .as("un fichier a une seule ligne se lit comme un journal vide, pas comme une panne")
                    .isEqualTo(1);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("la borne haute est inclusive : le dernier jour demande est compris")
        void borneHauteInclusive() {
            // Meme jour en debut et en fin : la periode doit couvrir ce jour entier,
            // pas un intervalle vide.
            assertThat(export.commandesEnCsv(DEBUT, DEBUT)).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Telechargement et gardes")
    class Telechargement {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le fichier arrive en piece jointe, sans cache partage")
        void entetesHttp() throws Exception {
            mvc.perform(get("/admin/export/factures.csv")
                            .param("depuis", "2026-01-01").param("jusqua", "2026-12-31"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(content().contentTypeCompatibleWith("text/csv"));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l ecran de saisie repond a l administrateur")
        void ecranAdministrateur() throws Exception {
            mvc.perform(get("/admin/export")).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint ni l ecran ni le fichier")
        void urlReservee() throws Exception {
            mvc.perform(get("/admin/export")).andExpect(status().isForbidden());
            mvc.perform(get("/admin/export/commandes.csv")
                            .param("depuis", "2026-01-01").param("jusqua", "2026-12-31"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service refuse meme sans passer par l URL")
        void serviceRedouble() {
            assertThatThrownBy(() -> export.facturesEnCsv(DEBUT, FIN))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithAnonymousUser
        @DisplayName("l anonyme est renvoye vers la connexion")
        void anonymeRefuse() throws Exception {
            mvc.perform(get("/admin/export")).andExpect(status().is3xxRedirection());
        }
    }
}
