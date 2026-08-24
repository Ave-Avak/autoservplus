package be.autoservplus.vitrine.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vitrine publique : accueil et page de contact.
 *
 * <p>Tests d <b>integration</b> et non {@code @WebMvcTest}, pour la meme raison que
 * les documents legaux : ce qui doit etre prouve n est pas qu une route est
 * declaree, mais qu elle rend un document contenant reellement les coordonnees du
 * garage et ses horaires. Un test a doublures resterait vert si la page se
 * construisait a vide.</p>
 *
 * <p>Les horaires attendus sont ceux du seed V10 — ouverture du lundi au vendredi
 * plus le samedi matin, dimanche ferme. Le test lit donc la meme source que
 * l agenda de reservation, ce qui est precisement le point defendu : deux sources
 * finiraient par diverger.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Vitrine publique (integration)")
class VitrineIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;

    @Nested
    @DisplayName("Page de contact")
    class Contact {

        /**
         * Anonyme, et pas seulement « non authentifie » : l article VI.45 CDE veut
         * que ces informations soient accessibles avant que le consommateur ne soit
         * lie, donc avant toute creation de compte.
         */
        @Test
        @DisplayName("est servie au visiteur sans compte")
        void serviePubliquement() throws Exception {
            mvc.perform(get("/contact").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }

        /**
         * Les mentions de l article VI.45 viennent de la configuration
         * {@code autoservplus.garage.*}, celle qui imprime l en-tete des factures.
         * Les valeurs attendues sont les valeurs de demonstration livrees : le test
         * echouerait si la page se mettait a ecrire ses propres coordonnees.
         */
        @Test
        @DisplayName("porte l identite legale lue de la configuration")
        void identiteLegale() throws Exception {
            mvc.perform(get("/contact").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("AutoServ+ SRL")))
                    .andExpect(content().string(containsString("BE0123456789")))
                    .andExpect(content().string(containsString("0123.456.789")))
                    .andExpect(content().string(containsString("1000 Bruxelles")));
        }

        @Test
        @DisplayName("affiche les horaires derives de plage_ouverture")
        void horairesDeLaBase() throws Exception {
            mvc.perform(get("/contact").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Lundi")))
                    // Matin et apres-midi restent distincts : le seed pose deux plages
                    // le lundi, les fusionner annoncerait une ouverture a midi.
                    .andExpect(content().string(containsString("08:00 – 12:00")))
                    .andExpect(content().string(containsString("13:00 – 18:00")));
        }

        /**
         * Le dimanche n a aucune plage au seed. La ligne doit exister et dire
         * « Fermé » : une ligne absente ne distingue pas une fermeture d une saisie
         * oubliee, et c est le lecteur qui paie la difference en se deplacant.
         */
        @Test
        @DisplayName("annonce Ferme le jour sans plage plutot que d omettre la ligne")
        void dimancheFerme() throws Exception {
            mvc.perform(get("/contact").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Dimanche")))
                    .andExpect(content().string(containsString("Fermé")));
        }

        /**
         * WCAG 3.1.1 : l attribut de langue suit la locale servie. Le libelle traduit
         * et l attribut sont verifies <b>ensemble</b> — les dissocier laisserait
         * revenir le defaut que F6 a corrige, une page annoncant une langue et en
         * servant une autre.
         */
        @Test
        @DisplayName("se traduit et declare la langue servie")
        void traduiteEtEtiquetee() throws Exception {
            mvc.perform(get("/contact?lang=nl").with(anonymous()))
                    .andExpect(content().string(containsString("lang=\"nl\"")))
                    .andExpect(content().string(containsString("Openingsuren")))
                    .andExpect(content().string(containsString("Gesloten")));
        }

        /**
         * Le plan d acces est un lien sortant, pas une carte embarquee : aucune
         * ressource tierce n est chargee, donc la CSP {@code script-src 'self'} reste
         * intacte et aucun traceur ne s installe sur une page publique.
         */
        @Test
        @DisplayName("propose un plan sans charger de ressource tierce")
        void planSansRessourceTierce() throws Exception {
            String page = mvc.perform(get("/contact").with(anonymous()).header("Accept-Language", "fr"))
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("openstreetmap.org/search")
                    .doesNotContain("<script src=\"http")
                    .doesNotContain("<iframe");
        }
    }

    @Nested
    @DisplayName("Accueil")
    class Accueil {

        /**
         * L accueil ne portait aucun lien public : ni catalogue, ni contact. La
         * moitie « vitrine » du site n etait atteignable qu en connaissant les
         * adresses.
         */
        @Test
        @DisplayName("mene au catalogue et a la page de contact")
        void navigationPublique() throws Exception {
            mvc.perform(get("/").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/services\"")))
                    .andExpect(content().string(containsString("href=\"/contact\"")));
        }

        @Test
        @DisplayName("presente le garage, en langue servie")
        void presentationTraduite() throws Exception {
            mvc.perform(get("/?lang=en").with(anonymous()))
                    .andExpect(content().string(containsString("lang=\"en\"")))
                    .andExpect(content().string(containsString("What you can do here")));
        }
    }
}
