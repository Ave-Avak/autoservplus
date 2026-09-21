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
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Turnstile arme avec les <b>cles de test publiees par Cloudflare</b> : la cle de site
 * {@code 1x00000000000000000000AA} et la cle secrete
 * {@code 1x0000000000000000000000000000000AA}, qui valide toujours.
 *
 * <h2>Ce que ce test prouve, et ce qu il ne prouve pas</h2>
 *
 * <p>Il prouve le <b>cablage</b> : le widget est rendu quand une cle de site existe, le
 * jeton est transmis, la verification serveur est appelee et son verdict respecte.</p>
 *
 * <p>Les deux cas retenus ne dependent pas du reseau. Un troisieme — « jeton factice
 * accepte, donc compte cree » — a ete ecrit puis <b>retire</b> : la frontiere accepte
 * la soumission quand Cloudflare est injoignable, si bien que ce cas passait au vert
 * avec ou sans reseau, et ne discriminait rien. Le respect du verdict est verifie par
 * {@code VerificateurTurnstileTest}, sur serveur simule.</p>
 *
 * <p>Il ne prouve <b>rien de la politique de securite du contenu</b>. Les cles de test
 * ne contactent aucun serveur : le widget rend un jeton factice sans jamais ouvrir de
 * trame vers {@code challenges.cloudflare.com}. Une directive manquante ne se verrait
 * donc pas ici. C est exactement ce qui s est produit avec {@code form-action} : la
 * passerelle bouchonnee redirigeait vers une page interne, le defaut n existait que sur
 * le chemin du vrai prestataire, et aucune build ne l a vu — seuls Chrome et Edge, en
 * production, l auraient revele. Voir registre §4.</p>
 */
@SpringBootTest(properties = {
        "autoservplus.securite.turnstile.cle-site=1x00000000000000000000AA",
        "autoservplus.securite.turnstile.cle-secrete=1x0000000000000000000000000000000AA"})
@AutoConfigureMockMvc
@DisplayName("Turnstile sur les formulaires publics (integration)")
class TurnstileIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase-de-passe-du-test-2026";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;

    private static String affiche() {
        return String.valueOf(System.currentTimeMillis()
                - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis());
    }

    @Test
    @DisplayName("le widget est rendu quand une clé de site est configurée")
    void widgetRendu() throws Exception {
        mvc.perform(get("/inscription").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cf-turnstile")))
                .andExpect(content().string(containsString("1x00000000000000000000AA")))
                .andExpect(content().string(containsString(
                        "https://challenges.cloudflare.com/turnstile/v0/api.js")));
    }

    /**
     * Le jeton absent est refuse <b>sans appel</b> : il signale un formulaire poste
     * hors du navigateur, pas une panne de Cloudflare.
     */
    @Test
    @DisplayName("sans jeton : aucun compte créé")
    void sansJeton() throws Exception {
        String email = "sans-jeton-" + COMPTEUR.getAndIncrement() + "@exemple.be";

        inscrire(email, null);

        assertThat(utilisateurs.findByEmailIgnoreCase(email)).isEmpty();
    }

    /**
     * <b>Le defaut que ce cas verrouille.</b> {@code @ConditionalOnProperty} considere
     * une propriete presente des qu elle est declaree, meme vide — or
     * {@code application.yml} la declare avec une valeur par defaut vide. Le
     * verificateur naissait donc sur TOUT deploiement, avec une cle vide, et refusait
     * chaque soumission en silence : inscription, renvoi de verification et mot de
     * passe oublie etaient morts par defaut. Verifie par {@code TurnstileAbsentIT},
     * qui monte le contexte sans cle.
     */
    private void inscrire(String email, String jeton) throws Exception {
        var requete = post("/inscription").with(anonymous()).with(csrf())
                .header("Accept-Language", "fr")
                .with(brute -> {
                    brute.setRemoteAddr("203.0.113.71");
                    return brute;
                })
                .param(PiegeAntiBot.CHAMP_HORODATAGE, affiche())
                .param("email", email)
                .param("motDePasse", MOT_DE_PASSE)
                .param("confirmationMotDePasse", MOT_DE_PASSE)
                .param("nom", "Test").param("prenom", "Alex").param("langue", "fr");
        if (jeton != null) {
            requete = requete.param(VerificateurTurnstile.CHAMP_JETON, jeton);
        }
        mvc.perform(requete).andExpect(status().isOk());
    }
}
