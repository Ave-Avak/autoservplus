package be.autoservplus.legal.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documents legaux publics servis et rendus reellement.
 *
 * <p>Le defaut corrige etait exactement celui-la : les trois adresses etaient
 * ouvertes dans la configuration de securite et citees par treize liens de
 * gabarits, sans controleur derriere. Un test qui se contenterait de verifier la
 * configuration serait donc <b>reste vert</b> tout le temps ou les pages
 * repondaient 404. C est le rendu complet qui fait foi ici, et c est pourquoi ces
 * cas sont des tests d integration et non des {@code @WebMvcTest} a doublures : ils
 * traversent Thymeleaf, la resolution des messages et la liaison de la
 * configuration.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Documents legaux publics (integration)")
class DocumentsLegauxIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;

    @Nested
    @DisplayName("Accessibles sans compte")
    class Ouvertes {

        /**
         * Anonyme et non simplement « non authentifie » : ces pages doivent etre
         * lisibles AVANT la creation du compte, puisque l inscription et la commande
         * demandent d en accepter le contenu.
         */
        @ParameterizedTest(name = "{0} repond 200")
        @ValueSource(strings = {"/cgv", "/mentions-legales", "/confidentialite"})
        @DisplayName("les trois adresses citees par les liens de gabarits sont servies")
        void servies(String adresse) throws Exception {
            mvc.perform(get(adresse).with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }

        /**
         * La banniere de brouillon est une exigence de fond, pas une decoration : un
         * document legal provisoire qui ne s annonce pas comme tel sera lu comme
         * definitif. Elle doit donc etre sur les trois pages, sans exception.
         */
        @ParameterizedTest(name = "{0} porte la banniere de brouillon")
        @ValueSource(strings = {"/cgv", "/mentions-legales", "/confidentialite"})
        @DisplayName("chaque page annonce qu elle est un brouillon")
        void brouillonAnnonce(String adresse) throws Exception {
            mvc.perform(get(adresse).with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("Document provisoire")));
        }
    }

    @Nested
    @DisplayName("Contenu assemble depuis les sources reelles")
    class SourcesReelles {

        /**
         * L identite affichee vient de {@code autoservplus.garage.*} — la meme
         * configuration qui imprime l en-tete des factures. Le test s appuie sur les
         * valeurs de demonstration livrees : si quelqu un recopiait une identite en
         * dur dans le gabarit, la surcharge par variable d environnement cesserait
         * d agir sans que rien ne le signale.
         */
        @Test
        @DisplayName("les mentions legales lisent l identite de la configuration")
        void identiteDepuisLaConfiguration() throws Exception {
            mvc.perform(get("/mentions-legales").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("BE0123456789")))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("0123.456.789")));
        }

        /**
         * La politique publiee et le registre joint a l export de l article 15 sont
         * resolus par le meme composant. Verifier ici une base legale du registre,
         * c est verifier que la page n a pas sa propre redaction parallele — laquelle
         * finirait tot ou tard par contredire l export.
         */
        @Test
        @DisplayName("la politique de confidentialite rend le registre des traitements")
        void registrePartageAvecLExport() throws Exception {
            mvc.perform(get("/confidentialite").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("article 6.1.b RGPD")))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("Mollie B.V.")));
        }

        /**
         * Le delai affiche est celui que la regle applique reellement. Un texte qui
         * annoncerait quatorze jours quand le code en refuse au bout de sept serait
         * une information trompeuse au sens du droit de la consommation.
         */
        @Test
        @DisplayName("le delai de retractation affiche est celui de la regle")
        void delaiAlignesSurLaRegle() throws Exception {
            mvc.perform(get("/cgv").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("délai de 14 jours")));
        }

        /**
         * Ce que le code ne sait pas reste un blanc VISIBLE. Le test verrouille la
         * marque, pour qu une relecture pressee ne puisse pas supprimer le marqueur
         * en laissant la section vide : une section vide se lit comme « rien a dire »,
         * un marqueur se lit comme « pas encore ecrit ».
         */
        @Test
        @DisplayName("l hebergeur inconnu reste un blanc marque, pas une invention")
        void placeholdersVisibles() throws Exception {
            mvc.perform(get("/mentions-legales").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("[À COMPLÉTER")));
        }
    }
}
