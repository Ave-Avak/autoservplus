package be.autoservplus.api.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API publique de bout en bout (BL-8), sur un PostgreSQL reel.
 *
 * <p>Tout est joue <b>en anonyme</b> : c est le seul mode d acces de cette API, et un
 * test authentifie ne prouverait pas qu elle est ouverte. Le test verifie aussi ce
 * qu elle ne doit <b>pas</b> exposer — donnee de gestion, verbe d ecriture — car c est
 * la que se situe le risque d une surface publique.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithAnonymousUser
@DisplayName("API publique v1 (integration)")
class ApiPubliqueIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;

    @Nested
    @DisplayName("Prestations")
    class Prestations {

        @Test
        @DisplayName("repond en anonyme, pagine, avec un lien self")
        void listePaginee() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.taille").value(20))
                    .andExpect(jsonPath("$.total").isNumber())
                    .andExpect(jsonPath("$.liens[*].rel", hasItem("self")));
        }

        @Test
        @DisplayName("n expose aucune donnee de gestion")
        void aucuneDonneeDeGestion() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("quantiteStock"))))
                    .andExpect(content().string(not(containsString("seuilAlerte"))))
                    .andExpect(content().string(not(containsString("disponible"))));
        }

        @Test
        @DisplayName("plafonne une taille de page excessive au lieu de refuser")
        void taillePlafonnee() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("taille", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taille").value(100));
        }

        @Test
        @DisplayName("ramene une page negative a la premiere")
        void pageNegative() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("page", "-5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0));
        }

        @Test
        @DisplayName("une page au-dela du dernier element rend une liste vide, pas une erreur")
        void pageAuDela() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("page", "9999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenu").isEmpty());
        }

        @Test
        @DisplayName("la premiere page ne porte pas de lien prev")
        void pasDePrevSurLaPremiere() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(jsonPath("$.liens[*].rel", not(hasItem("prev"))));
        }

        @Test
        @DisplayName("pagine reellement : une page de 1 ne rend qu un element")
        void paginationEffective() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("taille", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenu.length()").value(1))
                    .andExpect(jsonPath("$.liens[*].rel", hasItem("next")));
        }
    }

    @Nested
    @DisplayName("Garages")
    class Garages {

        @Test
        @DisplayName("rend une collection d un seul element, contrat deja multi-tenant")
        void collectionMonoTenant() throws Exception {
            mvc.perform(get("/api/v1/garages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.contenu[0].raisonSociale").isNotEmpty())
                    .andExpect(jsonPath("$.contenu[0].localite").isNotEmpty());
        }

        @Test
        @DisplayName("n expose ni IBAN ni numero BCE")
        void aucuneCoordonneeBancaire() throws Exception {
            mvc.perform(get("/api/v1/garages"))
                    .andExpect(content().string(not(containsString("iban"))))
                    .andExpect(content().string(not(containsString("BE68"))))
                    .andExpect(content().string(not(containsString("numeroBce"))));
        }
    }

    @Nested
    @DisplayName("Surface exposee")
    class Surface {

        @Test
        @DisplayName("aucun verbe d ecriture n est ouvert")
        void ecritureFermee() throws Exception {
            // Sans jeton CSRF et sans endpoint POST declare : la requete ne doit en
            // aucun cas aboutir a une creation.
            mvc.perform(post("/api/v1/prestations"))
                    .andExpect(status().is(not(is(200))));
        }

        @Test
        @DisplayName("la documentation OpenAPI ne decrit que /api/v1")
        void documentationBornee() throws Exception {
            mvc.perform(get("/v3/api-docs/public"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths./api/v1/prestations").exists())
                    .andExpect(jsonPath("$.paths./api/v1/garages").exists())
                    // Le webhook de paiement n est pas une API publique : le publier
                    // reviendrait a en signaler l existence.
                    .andExpect(jsonPath("$.paths./webhooks/paiement").doesNotExist());
        }

        @Test
        @DisplayName("les endpoints prives restent fermes a l anonyme")
        void privesToujoursFermes() throws Exception {
            mvc.perform(get("/mes-commandes")).andExpect(status().is3xxRedirection());
            mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("une route /api/v1 non declaree n est PAS publique par defaut")
        void surfaceFermeeParDefaut() throws Exception {
            // Verrouille le caractere fail-closed du matcher. Les deux statuts
            // distinguent exactement les deux ecritures possibles :
            //   .requestMatchers(GET, "/api/v1/**")  -> route permise, aucun handler -> 404
            //   .requestMatchers(GET, "/api/v1/prestations/**", "/api/v1/garages/**")
            //                                        -> route non permise, anonyme  -> 302
            // Sans ce test, un retour au joker republierait toute route future sans
            // que rien ne le signale.
            mvc.perform(get("/api/v1/pieces"))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(get("/api/v1/membres"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("la reponse autorise un cache public court")
        void cacheCourt() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().string("Cache-Control", containsString("public")));
        }
    }
}
