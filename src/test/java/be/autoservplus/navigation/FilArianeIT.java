package be.autoservplus.navigation;

import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Rendu du fil d'Ariane dans les deux en-têtes communs : niveaux dans l'ordre, liens vers
 * les niveaux précédents, page courante sans lien et marquée {@code aria-current},
 * libellé dynamique lu dans le modèle de la page, aucun fil sur une racine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Fil d'Ariane (intégration)")
class FilArianeIT extends SocleIntegration {

    private static final String FIL = "<nav class=\"fil-ariane fil-ariane-page\"";

    @Autowired private MockMvc mvc;
    @Autowired private PrestationRepository prestations;

    private String page(String adresse, RequestPostProcessor qui, Locale langue) throws Exception {
        var requete = get(adresse).locale(langue);
        if (qui != null) {
            requete = requete.with(qui);
        }
        return mvc.perform(requete).andReturn().getResponse().getContentAsString();
    }

    private static String fil(String html) {
        int debut = html.indexOf(FIL);
        assertThat(debut).as("fil d'Ariane rendu").isNotNegative();
        return html.substring(debut, html.indexOf("</nav>", debut));
    }

    @Test
    @DisplayName("espace membre : accueil, compte, puis la page courante sans lien")
    void espaceMembre() throws Exception {
        String fil = fil(page("/mes-rendez-vous", user("fil@exemple.be").roles("MEMBRE"), Locale.FRENCH));
        assertThat(fil)
                .contains("aria-label=\"Fil d&#39;Ariane\"")
                .contains("<a href=\"/\">Accueil</a>")
                .contains("<a href=\"/mon-compte\">Mon compte</a>")
                .contains("<span aria-current=\"page\">Mes rendez-vous</span>")
                .doesNotContain("href=\"/mes-rendez-vous\"");
        assertThat(fil.indexOf("Accueil")).isLessThan(fil.indexOf("Mon compte"));
    }

    @Test
    @DisplayName("libellé dynamique : la fiche d'une prestation porte son libellé")
    void libelleDynamique() throws Exception {
        var prestation = prestations.findByActifTrueOrderByLibelleAsc().get(0);
        String fil = fil(page("/services/" + prestation.getReference(), null, Locale.FRENCH));
        assertThat(fil)
                .contains("<a href=\"/services\">Nos prestations</a>")
                .contains("<span aria-current=\"page\">" + prestation.getLibelle() + "</span>");
    }

    @Test
    @DisplayName("back-office : le tableau de bord en tête, dans la langue de l'écran")
    void backOffice() throws Exception {
        String fil = fil(page("/admin/interventions", user("fil-admin@exemple.be").roles("ADMINISTRATEUR"),
                Locale.forLanguageTag("nl")));
        assertThat(fil).contains("href=\"/admin\"").contains("aria-current=\"page\"");
        assertThat(fil).doesNotContain("Tableau de bord");
    }

    @Test
    @DisplayName("aucun fil sur l'accueil, racine de l'espace public")
    void racineSansFil() throws Exception {
        assertThat(page("/", null, Locale.FRENCH)).doesNotContain(FIL);
    }

    @Test
    @DisplayName("aucun fil sur la page d'une référence inconnue")
    void erreurSansFil() throws Exception {
        String html = page("/mes-rendez-vous/" + UUID.randomUUID(), user("fil@exemple.be").roles("MEMBRE"),
                Locale.FRENCH);
        assertThat(html).doesNotContain(FIL);
    }
}
