package be.autoservplus.config;

import be.autoservplus.journal.service.JournalService;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La hierarchie {@code SUPER_ADMINISTRATEUR > ADMINISTRATEUR} couvre-t-elle les
 * trois surfaces d autorisation du projet : la chaine de filtres, la securite de
 * methode et le dialecte {@code sec:} des gabarits ?
 *
 * <p>Depuis Spring Security 6.3, un bean {@code RoleHierarchy} unique est repris par
 * l autorisation web et par la securite de methode. Le projet ne s en remet pas a
 * cette lecture : un comportement de framework suppose lui a deja coute trois
 * formulaires publics : {@code @ConditionalOnProperty} jugeait presente une propriete
 * declaree vide. Les surfaces sont donc verifiees separement, et une
 * regression sur l une seule ferait echouer un cas precis plutot que de passer
 * inapercue.</p>
 *
 * <p>Chaque surface porte aussi son cas de discrimination : sans lui, une
 * configuration qui autoriserait tout le monde satisferait les cas positifs.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Hierarchie des roles (integration)")
class HierarchieRolesIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private JournalService journal;

    @Nested
    @DisplayName("surface WEB : le matcher /admin/**")
    class SurfaceWeb {

        @Test
        @DisplayName("le super-administrateur atteint /admin sans que le matcher le nomme")
        void superAdminAtteintAdmin() throws Exception {
            mvc.perform(get("/admin").with(user("chef@exemple.be")
                            .roles("SUPER_ADMINISTRATEUR")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("l'administrateur y accède toujours : la hiérarchie n'a rien retiré")
        void administrateurInchange() throws Exception {
            mvc.perform(get("/admin").with(user("admin@exemple.be").roles("ADMINISTRATEUR")))
                    .andExpect(status().isOk());
        }

        /**
         * Sans ce cas, une hierarchie trop large (ou un matcher devenu permissif)
         * satisferait les deux precedents sans que rien ne le signale.
         */
        @Test
        @DisplayName("le membre reste refusé")
        void membreRefuse() throws Exception {
            mvc.perform(get("/admin").with(user("membre@exemple.be").roles("MEMBRE")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("surface METHODE : les 13 @PreAuthorize hasRole('ADMINISTRATEUR')")
    class SurfaceMethode {

        @Test
        @DisplayName("le super-administrateur passe la garde d'un service qui ne le nomme pas")
        @WithMockUser(username = "chef@exemple.be", roles = "SUPER_ADMINISTRATEUR")
        void superAdminPasseLaGarde() {
            assertThatCode(() -> journal.rechercher(null, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("l'administrateur passe toujours")
        @WithMockUser(username = "admin@exemple.be", roles = "ADMINISTRATEUR")
        void administrateurInchange() {
            assertThat(journal.rechercher(null, null, null, null)).isNotNull();
        }

        @Test
        @DisplayName("le membre est refusé")
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        void membreRefuse() {
            assertThatThrownBy(() -> journal.rechercher(null, null, null, null))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    /**
     * Le dialecte {@code sec:} evalue ses expressions avec le gestionnaire web de
     * Spring Security, auquel {@code WebSecurity} transmet le bean {@code RoleHierarchy}.
     * Rien d autre ne le verifiait : un lien conditionne a {@code ADMINISTRATEUR}
     * pouvait disparaitre pour le super-administrateur sans qu aucun test ne casse.
     */
    @Nested
    @DisplayName("surface GABARIT : le dialecte sec:")
    class SurfaceGabarit {

        private static final String LIEN_COMPTES_PRIVILEGIES = "href=\"/admin/comptes/administrateurs\"";

        @Test
        @DisplayName("le super-administrateur voit le lien qui lui est réservé")
        void superAdminVoitLeLien() throws Exception {
            mvc.perform(get("/admin").with(user("chef@exemple.be").roles("SUPER_ADMINISTRATEUR")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(LIEN_COMPTES_PRIVILEGIES)));
        }

        /** Le lien affiché menait l administrateur a un 403 : l ecran lui est refuse au service. */
        @Test
        @DisplayName("l'administrateur ne le voit pas")
        void administrateurNeLeVoitPas() throws Exception {
            mvc.perform(get("/admin").with(user("admin@exemple.be").roles("ADMINISTRATEUR")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(LIEN_COMPTES_PRIVILEGIES))));
        }
    }
}
