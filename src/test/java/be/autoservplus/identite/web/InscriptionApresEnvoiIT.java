package be.autoservplus.identite.web;

import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Après l'envoi du formulaire d'inscription, le navigateur est redirigé vers une page lue
 * en GET : l'actualiser ne renvoie pas le formulaire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Inscription : redirection après l'envoi (intégration)")
class InscriptionApresEnvoiIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe de la redirection";
    private static final String GENERIQUE =
            "Si vous venez de créer un compte, un courriel de vérification vous a été envoyé.";

    @Autowired private MockMvc mvc;

    private static MockHttpServletRequestBuilder envoi(String email, String ip, long delai) {
        return post("/inscription").with(anonymous()).with(csrf())
                .header("Accept-Language", "fr")
                .with(requete -> {
                    requete.setRemoteAddr(ip);
                    return requete;
                })
                .param(PiegeAntiBot.CHAMP_HORODATAGE, String.valueOf(System.currentTimeMillis() - delai))
                .param("email", email).param("motDePasse", MOT_DE_PASSE)
                .param("confirmationMotDePasse", MOT_DE_PASSE)
                .param("nom", "Dupont").param("prenom", "Marie").param("langue", "fr")
                .param("cgv", "true");
    }

    private String confirmation(MvcResult envoi) throws Exception {
        var requete = get("/inscription/confirmee").with(anonymous()).header("Accept-Language", "fr");
        if (envoi != null) {
            requete = requete.flashAttrs(envoi.getFlashMap());
        }
        return mvc.perform(requete).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static void sansFormulaire(String page) {
        assertThat(page).doesNotContain("action=\"/inscription\"").doesNotContain("id=\"motDePasse\"");
    }

    @Test
    @DisplayName("envoi accepté : redirection, puis la page affiche l'adresse sans formulaire")
    void redirectionPuisConfirmation() throws Exception {
        String email = "apres-envoi-" + COMPTEUR.getAndIncrement() + "@exemple.be";

        MvcResult envoi = mvc.perform(envoi(email, "203.0.113.190",
                        PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis()))
                .andExpect(redirectedUrl("/inscription/confirmee"))
                .andReturn();

        String page = confirmation(envoi);
        assertThat(page).contains(email).contains("Vérifiez votre courriel");
        sansFormulaire(page);
    }

    @Test
    @DisplayName("actualisée sans attribut flash : version générique, sans adresse ni formulaire")
    void actualisation() throws Exception {
        String page = confirmation(null);

        assertThat(page).contains(GENERIQUE).doesNotContain("a été envoyé à");
        sansFormulaire(page);
    }

    @ParameterizedTest(name = "« {0} »")
    @ValueSource(strings = {"", "pas-une-adresse"})
    @DisplayName("envoi automatique avec une adresse vide ou mal formée : version générique, dans la langue de la page")
    void envoiAutomatiqueSansAdresseValable(String adresse) throws Exception {
        MvcResult envoi = mvc.perform(envoi(adresse, "203.0.113.192", 0).param("lang", "nl"))
                .andExpect(redirectedUrl("/inscription/confirmee"))
                .andReturn();

        assertThat((Map<String, Object>) envoi.getFlashMap()).doesNotContainKey("adresse");
        String page = mvc.perform(get("/inscription/confirmee").param("lang", "nl").with(anonymous()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page)
                .contains("Als u net een account hebt aangemaakt, is er een verificatiemail naar u verzonden.")
                .doesNotContain("verzonden naar");
    }

    @Test
    @DisplayName("adresse vide reçue par la page : version générique, sans « envoyé à . »")
    void adresseVideDansLaPage() throws Exception {
        String page = mvc.perform(get("/inscription/confirmee").with(anonymous())
                        .header("Accept-Language", "fr").flashAttr("adresse", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains(GENERIQUE).doesNotContain("a été envoyé à");
    }

    @Test
    @DisplayName("envoi automatique écarté : même redirection qu'un envoi accepté")
    void envoiAutomatique() throws Exception {
        String email = "apres-envoi-" + COMPTEUR.getAndIncrement() + "@exemple.be";

        mvc.perform(envoi(email, "203.0.113.191", 0))
                .andExpect(redirectedUrl("/inscription/confirmee"));
    }
}
