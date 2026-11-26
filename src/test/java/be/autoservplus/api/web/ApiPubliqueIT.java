package be.autoservplus.api.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.support.SocleIntegration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API publique de bout en bout (BL-8), sur un PostgreSQL reel.
 *
 * <p>Tout est joue <b>en anonyme</b> : c est le seul mode d acces de cette API, et un
 * test authentifie ne prouverait pas qu elle est ouverte. Le test verifie aussi ce
 * qu elle ne doit <b>pas</b> exposer (donnee de gestion, verbe d ecriture) car c est
 * la que se situe le risque d une surface publique.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithAnonymousUser
@DisplayName("API publique v1 (integration)")
class ApiPubliqueIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private TransactionTemplate transactions;

    @Nested
    @DisplayName("Prestations")
    class Prestations {

        @Test
        @DisplayName("repond en anonyme, pagine, avec un lien self")
        void listePaginee() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.page.numero").value(0))
                    .andExpect(jsonPath("$.page.taille").value(20))
                    .andExpect(jsonPath("$.page.total").isNumber())
                    .andExpect(jsonPath("$._links.self.href").exists());
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
                    .andExpect(jsonPath("$.page.taille").value(100));
        }

        @Test
        @DisplayName("ramene une page negative a la premiere")
        void pageNegative() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("page", "-5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.numero").value(0));
        }

        @Test
        @DisplayName("une page au-dela du dernier element rend une liste vide, pas une erreur")
        void pageAuDela() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("page", "9999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.prestations").isEmpty());
        }

        @Test
        @DisplayName("le plus grand numéro de page rend une liste vide, pas une erreur")
        void pageDemesuree() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("page", "2147483647").param("taille", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.prestations").isEmpty());
        }

        @Test
        @DisplayName("la premiere page ne porte pas de lien prev")
        void pasDePrevSurLaPremiere() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(jsonPath("$._links.prev").doesNotExist());
        }

        @Test
        @DisplayName("pagine reellement : une page de 1 ne rend qu un element")
        void paginationEffective() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("taille", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.prestations.length()").value(1))
                    .andExpect(jsonPath("$._links.next.href").exists());
        }
    }

    @Nested
    @DisplayName("Langue de la réponse")
    class LangueDeLaReponse {

        private String code;

        @BeforeEach
        void semer() {
            code = "IT-API-" + UUID.randomUUID().toString().substring(0, 8);
            transactions.executeWithoutResult(statut -> {
                Categorie categorie = new Categorie(code, "Catégorie de l'API", TypeCategorie.SERVICE);
                categorie.traduire("Categorie van de API", null);
                categories.save(categorie);
                Prestation prestation = new Prestation(categorie, code, "Prestation de l'API",
                        new BigDecimal("10.00"), 30);
                prestation.traduire(new Traductions("Dienst van de API", null, "Beschrijving", null));
                prestations.save(prestation);
            });
        }

        /** La prestation semée, cherchée de page en page : la base des tests en porte d'autres. */
        private Map<String, Object> prestationSemee(String acceptLanguage) throws Exception {
            for (int page = 0; ; page++) {
                var requete = get("/api/v1/prestations").param("taille", "100").param("page", String.valueOf(page));
                if (acceptLanguage != null) {
                    requete.header("Accept-Language", acceptLanguage);
                }
                String corps = mvc.perform(requete).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
                List<Map<String, Object>> trouvees = JsonPath.read(corps, "$._embedded.prestations[?(@.code=='" + code + "')]");
                if (!trouvees.isEmpty()) {
                    return trouvees.getFirst();
                }
                List<Object> contenu = JsonPath.read(corps, "$._embedded.prestations");
                assertThat(contenu).as("prestation semée introuvable").isNotEmpty();
            }
        }

        @Test
        @DisplayName("Accept-Language nl : textes néerlandais, Content-Language nl, Vary sur l'en-tête")
        void neerlandais() throws Exception {
            mvc.perform(get("/api/v1/prestations").header("Accept-Language", "nl-BE, fr;q=0.8"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Language", "nl"))
                    .andExpect(header().stringValues("Vary", hasItem(containsString("Accept-Language"))));

            Map<String, Object> prestation = prestationSemee("nl-BE, fr;q=0.8");
            assertThat(prestation).containsEntry("libelle", "Dienst van de API")
                    .containsEntry("description", "Beschrijving")
                    .containsEntry("categorie", "Categorie van de API");
        }

        @Test
        @DisplayName("Accept-Language en sans traduction : Content-Language en, textes en français")
        void anglaisEnRepli() throws Exception {
            mvc.perform(get("/api/v1/prestations").header("Accept-Language", "en"))
                    .andExpect(header().string("Content-Language", "en"));

            assertThat(prestationSemee("en")).containsEntry("libelle", "Prestation de l'API")
                    .containsEntry("categorie", "Catégorie de l'API");
        }

        @Test
        @DisplayName("sans en-tête, langue non admise ou en-tête illisible : le français")
        void francaisParDefaut() throws Exception {
            mvc.perform(get("/api/v1/prestations"))
                    .andExpect(header().string("Content-Language", "fr"))
                    .andExpect(header().stringValues("Vary", hasItem(containsString("Accept-Language"))));
            mvc.perform(get("/api/v1/prestations").header("Accept-Language", "de, es;q=0.5"))
                    .andExpect(header().string("Content-Language", "fr"));
            mvc.perform(get("/api/v1/prestations").header("Accept-Language", "???"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Language", "fr"));

            assertThat(prestationSemee(null)).containsEntry("libelle", "Prestation de l'API");
        }

        @Test
        @DisplayName("la langue vient de l'en-tête seul : ?lang= ne change pas la réponse")
        void parametreLangIgnore() throws Exception {
            mvc.perform(get("/api/v1/prestations").param("lang", "nl").header("Accept-Language", "fr"))
                    .andExpect(header().string("Content-Language", "fr"));
        }

        @Test
        @DisplayName("la documentation OpenAPI décrit l'en-tête de la demande et ceux de la réponse")
        void documentation() throws Exception {
            mvc.perform(get("/v3/api-docs/public"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths./api/v1/prestations.get.parameters[*].name", hasItem("Accept-Language")))
                    .andExpect(jsonPath("$.paths./api/v1/prestations.get.responses.200.headers.Content-Language").exists())
                    .andExpect(jsonPath("$.paths./api/v1/prestations.get.responses.200.headers.Vary").exists());
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
                    .andExpect(jsonPath("$.page.total").value(1))
                    .andExpect(jsonPath("$._embedded.garages[0].raisonSociale").isNotEmpty())
                    .andExpect(jsonPath("$._embedded.garages[0].localite").isNotEmpty());
        }

        @Test
        @DisplayName("n'expose pas d'IBAN, et le numéro d'entreprise sert d'identifiant sans champ à son nom")
        void aucuneCoordonneeBancaire() throws Exception {
            mvc.perform(get("/api/v1/garages"))
                    .andExpect(content().string(not(containsString("iban"))))
                    .andExpect(content().string(not(containsString("BE68"))))
                    .andExpect(content().string(not(containsString("numeroBce"))));
        }

        @Test
        @DisplayName("le lien self du garage mène à sa ressource, un autre identifiant répond 404")
        void garageSeul() throws Exception {
            String corps = mvc.perform(get("/api/v1/garages")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            String self = JsonPath.read(corps, "$._embedded.garages[0]._links.self.href");
            String chemin = self.substring(self.indexOf("/api/v1/"));

            mvc.perform(get(chemin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.raisonSociale").isNotEmpty())
                    .andExpect(jsonPath("$._links.prestations.href", endsWith("/api/v1/prestations")));
            mvc.perform(get("/api/v1/garages/9999999999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Articles")
    class Articles {

        @Autowired private JdbcTemplate jdbc;

        @BeforeEach
        void semer() {
            transactions.executeWithoutResult(etat -> {
                jdbc.update("INSERT INTO categorie (code, libelle, libelle_nl, type, rayon) "
                        + "VALUES ('API_ACC', 'Intérieur', 'Interieur', 'PIECE', 'ACCESSOIRES')");
                jdbc.update("INSERT INTO piece (categorie_id, reference_fabricant, libelle, libelle_nl, prix_htva, "
                        + "quantite_stock) SELECT id, 'API-TAPIS', 'Tapis de sol', 'Vloermatten', 33.00, 7 "
                        + "FROM categorie WHERE code = 'API_ACC'");
            });
        }

        // La classe ne tourne pas dans une transaction annulée : l'article semé est retiré,
        // pour ne pas compter dans les listes d'autres classes du même conteneur.
        @AfterEach
        void retirer() {
            jdbc.update("DELETE FROM piece WHERE reference_fabricant = 'API-TAPIS'");
            jdbc.update("DELETE FROM categorie WHERE code = 'API_ACC'");
        }

        @Test
        @DisplayName("filtre par rayon, rayon et catégorie exposés, disponibilité sans quantité")
        void parRayon() throws Exception {
            mvc.perform(get("/api/v1/articles").param("rayon", "ACCESSOIRES").param("taille", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.articles[*].rayon", everyItem(is("ACCESSOIRES"))))
                    .andExpect(jsonPath("$._embedded.articles[?(@.code == 'API-TAPIS')].categorie", hasItem("Intérieur")))
                    .andExpect(jsonPath("$._embedded.articles[?(@.code == 'API-TAPIS')].disponible", hasItem(true)))
                    .andExpect(jsonPath("$._embedded.articles[?(@.code == 'API-TAPIS')].prixTvac", hasItem(39.93)))
                    .andExpect(content().string(not(containsString("quantiteStock"))))
                    .andExpect(content().string(not(containsString("seuilAlerte"))));

            mvc.perform(get("/api/v1/articles").param("rayon", "PIECES_AUTO").param("taille", "100"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("API-TAPIS"))));
        }

        @Test
        @DisplayName("sans rayon, tous les produits, dans la langue négociée")
        void tousLesRayons() throws Exception {
            mvc.perform(get("/api/v1/articles").param("taille", "100").header("Accept-Language", "nl-BE"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Language", "nl"))
                    .andExpect(jsonPath("$._embedded.articles[?(@.code == 'API-TAPIS')].libelle", hasItem("Vloermatten")))
                    .andExpect(jsonPath("$._embedded.articles[?(@.code == 'API-TAPIS')].categorie", hasItem("Interieur")));
        }

        @Test
        @DisplayName("documentée dans OpenAPI")
        void documentation() throws Exception {
            mvc.perform(get("/v3/api-docs/public"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths./api/v1/articles").exists());
        }

        @Test
        @DisplayName("un produit seul, en anonyme, avec ses liens, 404 inconnu ou retiré")
        void produitSeul() throws Exception {
            UUID reference = jdbc.queryForObject(
                    "SELECT reference FROM piece WHERE reference_fabricant = 'API-TAPIS'", UUID.class);
            mvc.perform(get("/api/v1/articles/{reference}", reference))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("API-TAPIS"))
                    .andExpect(jsonPath("$._links.self.href", endsWith("/api/v1/articles/" + reference)))
                    .andExpect(jsonPath("$._links.fiche.href", endsWith("/accessoires/" + reference)));
            mvc.perform(get("/api/v1/articles/{reference}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/articles/pas-une-reference"))
                    .andExpect(status().isNotFound());

            jdbc.update("UPDATE piece SET actif = false WHERE reference_fabricant = 'API-TAPIS'");
            mvc.perform(get("/api/v1/articles/{reference}", reference))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Disponibilités (F43)")
    class Disponibilites {

        @Test
        @DisplayName("répond en anonyme, en JSON-LD, avec ETag et cache public court")
        void anonyme() throws Exception {
            mvc.perform(get("/api/v1/disponibilites"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
                    .andExpect(header().exists("ETag"))
                    .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                    .andExpect(jsonPath("$['@type']").value("ItemList"))
                    .andExpect(jsonPath("$.itemListElement").isArray());
        }

        @Test
        @DisplayName("prestation inconnue : 404, référence mal formée : 400")
        void prestationInconnue() throws Exception {
            mvc.perform(get("/api/v1/disponibilites").param("prestation", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/disponibilites").param("prestation", "pas-une-reference"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("route exacte : rien n'est public sous elle")
        void routeExacte() throws Exception {
            mvc.perform(get("/api/v1/disponibilites/postes"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("le garage y mène par ses liens")
        void lienDuGarage() throws Exception {
            mvc.perform(get("/api/v1/garages"))
                    .andExpect(jsonPath("$._embedded.garages[0]._links.disponibilites.href",
                            endsWith("/api/v1/disponibilites")));
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
