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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La hierarchie {@code SUPER_ADMINISTRATEUR > ADMINISTRATEUR} couvre-t-elle les
 * <b>deux</b> surfaces d autorisation du projet ?
 *
 * <p>Depuis Spring Security 6.3, un bean {@code RoleHierarchy} unique est repris par
 * l autorisation web et par la securite de methode. Le projet ne s en remet pas a
 * cette lecture : un comportement de framework <i>suppose</i> lui a deja coute trois
 * formulaires publics — {@code @ConditionalOnProperty} jugeant presente une propriete
 * declaree vide. Les deux surfaces sont donc verifiees <b>separement</b>, et une
 * regression sur l une seule ferait echouer un cas precis plutot que de passer
 * inapercue.</p>
 *
 * <p>Chaque surface porte aussi son cas de <b>discrimination</b> : sans lui, une
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
         * Sans ce cas, une hierarchie trop large — ou un matcher devenu permissif —
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

}
