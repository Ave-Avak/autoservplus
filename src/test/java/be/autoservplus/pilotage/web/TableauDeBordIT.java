package be.autoservplus.pilotage.web;

import be.autoservplus.pilotage.repository.IndicateursRepository;
import be.autoservplus.pilotage.service.TableauDeBordService;
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

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tableau de bord de bout en bout (BL-1), sur un PostgreSQL reel.
 *
 * <p>L essentiel de ce qui peut casser ici est <b>dans les requetes</b> : expressions
 * de construction JPQL, {@code EXTRACT(EPOCH …)} pour convertir un intervalle en
 * minutes, agregats sur des tables vides. Aucune ne se verifie a la compilation, et un
 * mock de repository les rendrait invisibles — d ou un test qui les execute vraiment,
 * y compris sur une base sans donnee.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Tableau de bord (integration)")
class TableauDeBordIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant DEBUT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FIN = Instant.parse("2027-01-01T00:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private IndicateursRepository indicateurs;
    @Autowired private TableauDeBordService service;

    @Nested
    @DisplayName("Requetes d agregation")
    class Agregations {

        @Test
        @DisplayName("toutes les agregations s executent sur PostgreSQL")
        void toutesLesRequetesPassent() {
            assertThatCode(() -> {
                indicateurs.chiffreAffaireFacture(DEBUT, FIN);
                indicateurs.avoirsEmis(DEBUT, FIN);
                indicateurs.rendezVousParStatut(DEBUT, FIN);
                indicateurs.minutesReservees(DEBUT, FIN);
                indicateurs.topPrestations(DEBUT, FIN, 5);
                indicateurs.topPieces(DEBUT, FIN, 5);
                indicateurs.commandesAEncaisser();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("une periode sans donnee rend zero, jamais null")
        void periodeVide() {
            Instant tresAncien = Instant.parse("1999-01-01T00:00:00Z");
            Instant unPeuMoins = Instant.parse("1999-02-01T00:00:00Z");

            var ca = indicateurs.chiffreAffaireFacture(tresAncien, unPeuMoins);

            assertThat(ca.htva()).isNotNull().isEqualByComparingTo("0");
            assertThat(ca.tvac()).isNotNull().isEqualByComparingTo("0");
            assertThat(ca.nombre()).isZero();
            assertThat(ca.estVide()).isTrue();
            assertThat(indicateurs.minutesReservees(tresAncien, unPeuMoins)).isZero();
        }
    }

    @Nested
    @DisplayName("Assemblage et gardes")
    class Assemblage {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le service assemble un tableau complet sur la base de demo")
        void assemblageComplet() {
            var bord = service.duMois(Locale.FRENCH);

            assertThat(bord.mois()).isNotBlank();
            assertThat(bord.chiffreAffaire()).isNotNull();
            assertThat(bord.aEncaisser()).isNotNull();
            assertThat(bord.piecesEnAlerte()).isNotNull();
            // V17 seede trois postes de demonstration : la capacite doit etre connue.
            assertThat(bord.postesActifs()).isPositive();
            assertThat(bord.capaciteConnue())
                    .as("des plages d ouverture et des postes existent au seed")
                    .isTrue();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l ecran /admin repond a l administrateur")
        void ecranAdministrateur() throws Exception {
            mvc.perform(get("/admin")).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint pas /admin")
        void urlReservee() throws Exception {
            mvc.perform(get("/admin")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service refuse meme sans passer par l URL")
        void serviceRedouble() {
            assertThatThrownBy(() -> service.duMois(Locale.FRENCH))
                    .as("le chiffre d affaires du garage n a rien a faire devant un membre")
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithAnonymousUser
        @DisplayName("l anonyme est renvoye vers la connexion")
        void anonymeRefuse() throws Exception {
            mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        }
    }
}
