package be.autoservplus.identite.web;

import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le formulaire d'inscription et sa page de confirmation suivent la langue de l'interface
 * (F6), titre de l'onglet, corps et messages d'erreur compris.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Pages d'inscription traduites (intégration)")
class InscriptionTraduiteIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe des pages traduites";

    @Autowired private MockMvc mvc;

    private static MockHttpServletRequestBuilder envoi(String langue, String confirmation) {
        int numero = COMPTEUR.getAndIncrement();
        return post("/inscription").param("lang", langue)
                .with(anonymous()).with(csrf())
                // Une adresse IP par envoi : le plafond par IP est partagé par le contexte.
                .with(requete -> {
                    requete.setRemoteAddr("203.0.113." + (150 + numero));
                    return requete;
                })
                .param(PiegeAntiBot.CHAMP_HORODATAGE, String.valueOf(System.currentTimeMillis()
                        - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis()))
                .param("email", "inscription-traduite-" + numero + "@exemple.be")
                .param("motDePasse", MOT_DE_PASSE)
                .param("confirmationMotDePasse", confirmation)
                .param("nom", "Dupont").param("prenom", "Marie").param("langue", langue).param("cgv", "true");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "fr, Créer un compte, Prénom",
            "nl, Account aanmaken, Voornaam",
            "en, Create an account, First name"})
    @DisplayName("le formulaire est rendu dans la langue choisie")
    void formulaire(String langue, String titre, String prenom) throws Exception {
        mvc.perform(get("/inscription").param("lang", langue).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<html lang=\"" + langue + "\""),
                        containsString("<title>" + titre + " | AutoServ+</title>"),
                        containsString(">" + prenom + "</label>"))));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"fr", "nl", "en"})
    @DisplayName("la langue du compte proposée est celle de la page")
    void langueProposee(String langue) throws Exception {
        String page = mvc.perform(get("/inscription").param("lang", langue).with(anonymous()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<option value=\"" + langue + "\" selected=\"selected\">");
        assertThat(page.split("selected=\"selected\"", -1)).hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "nl, De twee wachtwoorden komen niet overeen.",
            "en, The two passwords do not match."})
    @DisplayName("l'erreur de confirmation du mot de passe est traduite")
    void erreurTraduite(String langue, String erreur) throws Exception {
        mvc.perform(envoi(langue, MOT_DE_PASSE + " autre"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString(erreur),
                        not(containsString("ne correspondent pas")))));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "nl, Uw account is bijna klaar, Er is een verificatiemail verzonden naar",
            "en, Your account is almost ready, A verification email has been sent to"})
    @DisplayName("la page de confirmation est traduite")
    void confirmationTraduite(String langue, String titre, String envoye) throws Exception {
        var envoiFait = mvc.perform(envoi(langue, MOT_DE_PASSE)).andReturn();

        mvc.perform(get("/inscription/confirmee").param("lang", langue).with(anonymous())
                        .flashAttrs(envoiFait.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<title>" + titre + " | AutoServ+</title>"),
                        containsString(envoye),
                        not(containsString("Votre compte est presque prêt")))));
    }
}
