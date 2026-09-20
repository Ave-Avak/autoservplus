package be.autoservplus.identite.web;

import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.identite.service.VerificateurTurnstile;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deploiement <b>sans</b> Turnstile : le cas par defaut, et celui qui etait casse.
 *
 * <p>{@code application.yml} declare {@code cle-secrete} avec une valeur par defaut
 * vide. Un {@code @ConditionalOnProperty} sur le seul nom la jugeait presente : le
 * verificateur naissait avec une cle vide, aucun jeton ne lui parvenait puisque le
 * widget n etait pas rendu, et il refusait <b>chaque soumission, en silence, sur la
 * page du cas nominal</b>. Inscription, renvoi de verification et mot de passe oublie
 * etaient morts sur tout deploiement sans Turnstile.</p>
 *
 * <p>Une cle vide n est pas une cle : la condition le dit desormais, et ce test le
 * verifie par l effet — un compte est reellement cree.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Formulaires publics sans Turnstile configure (integration)")
class TurnstileAbsentIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase-de-passe-sans-turnstile";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private ApplicationContext contexte;

    @Test
    @DisplayName("aucune clé : le vérificateur n'existe pas")
    void verificateurAbsent() {
        assertThat(contexte.getBeanNamesForType(VerificateurTurnstile.class))
                .as("Une clé vide n'est pas une clé : le vérificateur ne doit pas naître")
                .isEmpty();
    }

    @Test
    @DisplayName("aucune clé : le widget n'est pas rendu")
    void widgetAbsent() throws Exception {
        mvc.perform(get("/inscription").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("cf-turnstile"))))
                .andExpect(content().string(not(containsString("challenges.cloudflare.com"))));
    }

    /**
     * Le cas qui fait le travail : sans jeton et sans Turnstile, l inscription aboutit.
     * C est exactement ce qui ne marchait plus.
     */
    @Test
    @DisplayName("aucune clé : l'inscription aboutit sans jeton")
    void inscriptionAboutit() throws Exception {
        String email = "sans-turnstile-" + COMPTEUR.getAndIncrement() + "@exemple.be";

        mvc.perform(post("/inscription").with(anonymous()).with(csrf())
                        .header("Accept-Language", "fr")
                        .with(brute -> {
                            brute.setRemoteAddr("203.0.113.81");
                            return brute;
                        })
                        .param(PiegeAntiBot.CHAMP_HORODATAGE,
                                String.valueOf(System.currentTimeMillis()
                                        - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis()))
                        .param("email", email)
                        .param("motDePasse", MOT_DE_PASSE)
                        .param("confirmationMotDePasse", MOT_DE_PASSE)
                        .param("nom", "Test").param("prenom", "Alex").param("langue", "fr"))
                .andExpect(status().isOk());

        assertThat(utilisateurs.findByEmailIgnoreCase(email)).isPresent();
    }
}
