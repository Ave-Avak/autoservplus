package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import be.autoservplus.support.SocleIntegration;

/**
 * En-tetes de securite reellement emis sur une reponse servie.
 *
 * <p>Test d <b>integration</b> et non {@code @WebMvcTest} : ce qui doit etre prouve
 * n est pas que {@code SecuriteConfig} declare une politique, mais qu un navigateur
 * la recoit. Un test a doublures instancie sa propre chaine de filtres (celle des
 * {@code SecuriteTest} qui parsement les {@code @WebMvcTest} du projet) et resterait
 * vert quelle que soit la politique reelle.</p>
 *
 * <p><b>Pourquoi {@code form-action} merite un test a lui seul.</b> Le depart vers le
 * paiement est un POST dont la reponse redirige vers Mollie. Chrome et Edge
 * appliquent {@code form-action} a la cible de cette redirection, Firefox non. Le
 * defaut ne se manifeste donc ni dans {@code verify}, ni sur la passerelle
 * bouchonnee (qui redirige vers une page interne, couverte par {@code 'self'}) ni
 * meme sur un navigateur. Il a fallu une repetition de deploiement avec le vrai
 * prestataire, menee depuis Chrome, pour le voir. A defaut de pouvoir rejouer cela
 * dans une suite de tests, c est la politique elle-meme qui est verrouillee ici.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("En-tetes de securite (integration)")
class EnTetesSecuriteIT extends SocleIntegration {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("form-action autorise le site et le seul hote du prestataire de paiement")
    void formActionAutoriseMollie() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        containsString("form-action 'self' https://www.mollie.com")));
    }

    @Test
    @DisplayName("aucune autre directive n est relachee au passage")
    void resteDeLaPolitiqueInchange() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        // HTMX est servi en local : aucun CDN n a jamais ete autorise.
                        // Le seul hote ajoute depuis est celui de Turnstile, verifie
                        // par le test dedie ci-dessous.
                        containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("base-uri 'self'")))
                // Un joker sur form-action rendrait le test precedent vert tout en
                // autorisant l envoi d un formulaire vers n importe quel tiers.
                .andExpect(header().string("Content-Security-Policy", not(containsString("*"))));
    }

    /**
     * Turnstile exige deux directives, et une seule et meme origine dans les deux.
     *
     * <p><b>Ce que ce test ne prouve pas</b>, et c est le point : les cles de test de
     * Cloudflare ne contactent rien (pas d iframe, jeton factice) donc aucun test ne
     * peut reveler qu il manque une directive. C est exactement ce qui s est produit
     * avec {@code form-action} : la passerelle bouchonnee redirigeait vers une page
     * interne, le defaut n existait que sur le chemin du vrai prestataire, et aucune
     * build ne l a vu. La completude de cette politique ne se verifie qu avec une
     * vraie clé de site, sur l'hôte localhost, en observant le widget réel.</p>
     */
    @Test
    @DisplayName("Turnstile : script-src et frame-src autorisent le seul hote de Cloudflare")
    void turnstile() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("script-src 'self' https://challenges.cloudflare.com")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-src https://challenges.cloudflare.com")))
                // Declaree explicitement : sans elle, connect-src retombait sur
                // default-src, et la politique ne disait pas ce qu elle autorisait.
                .andExpect(header().string("Content-Security-Policy",
                        containsString("connect-src 'self'")))
                // Charger la trame d un tiers chez nous n autorise pas un tiers a
                // charger nos pages chez lui.
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("les autres en-tetes de securite sont poses")
    void autresEntetes() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }

    /**
     * Les styles et les images, présents dans la politique et jusqu'ici absents des
     * assertions. Le garde-fou {@code not("*")} ne voit pas {@code 'unsafe-inline'}, le
     * raccourci qu'on prend sous la pression d'un style récalcitrant : il est refusé
     * nommément, avec {@code 'unsafe-eval'}.
     */
    @Test
    @DisplayName("style-src et img-src tenus, aucune exception unsafe-inline ni unsafe-eval")
    void stylesEtImages() throws Exception {
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("style-src 'self';")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("img-src 'self' data:;")))
                .andExpect(header().string("Content-Security-Policy",
                        not(containsString("unsafe-inline"))))
                .andExpect(header().string("Content-Security-Policy",
                        not(containsString("unsafe-eval"))));
    }

    /**
     * HSTS n'est émis que sur une requête jugée sûre, d'où {@code secure(true)} : c'est
     * ce qui expliquait l'absence de test, et non un oubli. Le cas en clair vérifie
     * l'autre moitié du contrat, la norme interdisant l'en-tête hors HTTPS.
     */
    @Test
    @DisplayName("HSTS d'un an, sous-domaines compris, sur HTTPS seulement")
    void hsts() throws Exception {
        mvc.perform(get("/").with(anonymous()).secure(true))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("includeSubDomains")));
        mvc.perform(get("/").with(anonymous()))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
