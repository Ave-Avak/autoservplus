package be.autoservplus.journal.web;

import be.autoservplus.journal.service.JournalService;
import be.autoservplus.journal.service.dto.EntreeJournal;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Journal d audit de bout en bout (BL-7), sur un PostgreSQL reel.
 *
 * <p>Le risque est concentre dans <b>l UNION native</b> : elle est assemblee
 * dynamiquement selon le filtre de type, et aucune de ses variantes ne se verifie a la
 * compilation. Le test execute les quatre combinaisons, y compris sur une base sans
 * historique.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Journal d audit (integration)")
class JournalIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private JournalService journal;

    @Nested
    @DisplayName("Requetes et filtres")
    class Filtres {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("les quatre variantes de l UNION s executent")
        void toutesLesVariantes() {
            assertThatCode(() -> {
                journal.rechercher(null, null, null, null);
                journal.rechercher(EntreeJournal.TYPE_CATALOGUE, null, null, null);
                journal.rechercher(EntreeJournal.TYPE_INTERVENTION, null, null, null);
                journal.rechercher("", "Dupont", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31));
            }).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("un type inconnu ne fait pas exploser la requete, il ne rend rien")
        void typeInconnu() {
            List<EntreeJournal> entrees = journal.rechercher("N_EXISTE_PAS", null, null, null);

            assertThat(entrees).isEmpty();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le filtre de type restreint bien la source")
        void filtreDeType() {
            List<EntreeJournal> catalogue = journal.rechercher(
                    EntreeJournal.TYPE_CATALOGUE, null, null, null);

            assertThat(catalogue).allMatch(e -> EntreeJournal.TYPE_CATALOGUE.equals(e.type()));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("une periode sans trace rend une liste vide, pas une erreur")
        void periodeVide() {
            assertThat(journal.rechercher(null, null,
                    LocalDate.of(1999, 1, 1), LocalDate.of(1999, 1, 31))).isEmpty();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le plafond de lignes est respecte")
        void plafond() {
            assertThat(journal.rechercher(null, null, null, null))
                    .hasSizeLessThanOrEqualTo(JournalService.LIMITE);
        }
    }

    @Nested
    @DisplayName("Ecran et gardes")
    class Ecran {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l ecran repond a l administrateur, avec et sans filtre")
        void ecranAdministrateur() throws Exception {
            mvc.perform(get("/admin/journal")).andExpect(status().isOk());
            mvc.perform(get("/admin/journal")
                            .param("type", "INTERVENTION")
                            .param("acteur", "Dupont")
                            .param("depuis", "2026-01-01")
                            .param("jusqua", "2026-12-31"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint pas le journal")
        void urlReservee() throws Exception {
            mvc.perform(get("/admin/journal")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service refuse meme sans passer par l URL")
        void serviceRedouble() {
            assertThatThrownBy(() -> journal.rechercher(null, null, null, null))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithAnonymousUser
        @DisplayName("l anonyme est renvoye vers la connexion")
        void anonymeRefuse() throws Exception {
            mvc.perform(get("/admin/journal")).andExpect(status().is3xxRedirection());
        }
    }
}
