package be.autoservplus.legal.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les liens légaux (F24) sont au pied de toutes les pages, publiques comme membres, dans
 * cet ordre : CGV, mentions légales, politique de confidentialité, garantie légale,
 * gestion des cookies. Ils sont portés par la ligne commune du fragment des cookies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Liens légaux au pied de toutes les pages (intégration)")
class LiensLegauxIT extends SocleIntegration {

    private static final String MEMBRE = "liens-legaux@exemple.be";
    private static final Pattern LIGNE_LEGALE =
            Pattern.compile("<nav class=\"pied pied-cookies\"[^>]*>(.*?)</nav>", Pattern.DOTALL);
    private static final Pattern LIEN = Pattern.compile("href=\"([^\"]+)\"");
    private static final List<String> ATTENDUS =
            List.of("/cgv", "/mentions-legales", "/confidentialite", "/garantie-legale", "/cookies");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;

    private String ligneLegale(MockHttpServletRequestBuilder requete) throws Exception {
        String page = mvc.perform(requete).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher ligne = LIGNE_LEGALE.matcher(page);
        assertThat(ligne.find()).as("ligne légale présente").isTrue();
        return ligne.group(1);
    }

    private static List<String> liens(String ligne) {
        return LIEN.matcher(ligne).results().map(lien -> lien.group(1)).toList();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/", "/services", "/pieces", "/connexion", "/inscription", "/contact",
            "/cgv", "/garantie-legale", "/cookies", "/mot-de-passe/oublie"})
    @DisplayName("pages publiques : les cinq liens, dans l'ordre")
    void pagesPubliques(String adresse) throws Exception {
        String ligne = ligneLegale(get(adresse).with(anonymous()).header("Accept-Language", "fr"));

        assertThat(liens(ligne)).containsExactlyElementsOf(ATTENDUS);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/mon-compte", "/panier", "/commandes"})
    @DisplayName("pages membres : les cinq liens, dans l'ordre")
    void pagesMembres(String adresse) throws Exception {
        if (utilisateurs.findByEmail(MEMBRE).isEmpty()) {
            Utilisateur membre = new Utilisateur(MEMBRE, "$2a$12$abcdefghijklmnopqrstuv",
                    "Test", "Alex", TypeUtilisateur.MEMBRE);
            membre.confirmerAdresseEmail();
            utilisateurs.save(membre);
        }

        String ligne = ligneLegale(get(adresse).with(user(MEMBRE).roles("MEMBRE"))
                .header("Accept-Language", "fr"));

        assertThat(liens(ligne)).containsExactlyElementsOf(ATTENDUS);
    }

    @Test
    @DisplayName("libellés dans la langue de la page")
    void libellesTraduits() throws Exception {
        String ligne = ligneLegale(get("/pieces").param("lang", "nl").with(anonymous()));

        assertThat(ligne).contains(">Algemene verkoopvoorwaarden<", ">Wettelijke vermeldingen<",
                ">Privacybeleid<", ">Wettelijke garantie<", ">Mijn cookies beheren<");
    }
}
