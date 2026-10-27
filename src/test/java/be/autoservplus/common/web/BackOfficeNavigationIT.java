package be.autoservplus.common.web;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Menu du back-office, carte « Back-office » de « Mon compte » et lien de l en-tete
 * public, selon le role.
 *
 * <p>L ouverture effective de chaque lien est l affaire de {@code MenusCoherentsIT}.
 * Ici, seulement ce que chaque role voit et ne voit pas.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Navigation du back-office (integration)")
class BackOfficeNavigationIT extends SocleIntegration {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("le menu porte ses sections et marque le tableau de bord comme page active")
    void menuDuBackOffice() throws Exception {
        mvc.perform(get("/admin").with(user("admin@exemple.be").roles("ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("aria-label=\"Sections du back-office\""),
                        containsString("<a href=\"/admin\" aria-current=\"page\">Tableau de bord</a>"),
                        containsString("href=\"/admin/catalogue/prestations\""),
                        containsString("href=\"/admin/import\""),
                        containsString("href=\"/admin/commandes-a-planifier\""),
                        containsString("href=\"/admin/export\""),
                        containsString("href=\"/admin/journal\""),
                        containsString(">Voir le site</a>"),
                        not(containsString("href=\"/admin/comptes/administrateurs\"")))));
    }

    @Test
    @DisplayName("une fiche de détail marque l'entrée de sa liste")
    void entreeActiveSurUnDetail() throws Exception {
        mvc.perform(get("/admin/comptes/administrateurs").with(user("chef@exemple.be")
                        .roles("SUPER_ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<a href=\"/admin/comptes/administrateurs\" aria-current=\"page\">")));
    }

    @Test
    @DisplayName("« Mon compte » d'un administrateur : profil, sécurité et back-office seulement")
    void monCompteAdministrateur() throws Exception {
        mvc.perform(get("/mon-compte").with(user("admin@exemple.be").roles("ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("class=\"carte carte-lien\" href=\"/mon-profil\""),
                        containsString("class=\"carte carte-lien\" href=\"/mon-profil/securite\""),
                        containsString("class=\"carte carte-lien\" href=\"/admin\""),
                        not(containsString("href=\"/mes-vehicules\"")),
                        not(containsString("href=\"/admin/rendez-vous\"")))));
    }

    @Test
    @DisplayName("l'en-tête public mène au back-office l'administrateur, et lui seul")
    void lienBackOfficeDeLEnTete() throws Exception {
        mvc.perform(get("/contact").with(user("chef@exemple.be").roles("SUPER_ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(content().string(containsString("<a href=\"/admin\">Back-office</a>")));
        mvc.perform(get("/contact").with(user("membre@exemple.be").roles("MEMBRE"))
                        .locale(Locale.FRENCH))
                .andExpect(content().string(not(containsString("href=\"/admin\""))));
    }
}
