package be.autoservplus.vente.service;

import be.autoservplus.config.MollieProprietes;
import be.autoservplus.vente.domain.StatutPaiement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Dialogue reel avec l API Mollie, verifie sans reseau ni cle : le client HTTP est
 * substitue par {@link MockRestServiceServer}, qui controle la requete emise autant
 * que la reponse rendue.
 *
 * <p>C est la moitie que ni le bouchon ni un test d integration ne couvrent —
 * l integration ne peut pas verifier qu on envoie {@code profileId}, et le bouchon
 * ne parle pas Mollie.</p>
 */
@DisplayName("Passerelle Mollie")
class MollieGatewayTest {

    private static final String API = "https://api.mollie.com/v2";

    private MockRestServiceServer serveur;
    private RestClient.Builder constructeur;

    @BeforeEach
    void setUp() {
        constructeur = RestClient.builder().baseUrl(API);
        serveur = MockRestServiceServer.bindTo(constructeur).build();
    }

    private MollieGateway passerelleAvecJetonOrganisation() {
        return new MollieGateway(constructeur.build(),
                new MollieProprietes("access_jeton", "pfl_demo", true));
    }

    private MollieGateway passerelleAvecCleApi() {
        return new MollieGateway(constructeur.build(),
                new MollieProprietes("test_cle", null, true));
    }

    private static DemandePaiement demande(String urlNotification) {
        return new DemandePaiement("CMD-2026-0001", new BigDecimal("80.21"), "EUR",
                "cle-idempotence-1", "https://garage.example/commande/abc/retour",
                urlNotification);
    }

    private static String paiementJson(String statut) {
        return """
                {"id":"tr_reel1","status":"%s",
                 "amount":{"currency":"EUR","value":"80.21"},
                 "_links":{"checkout":{"href":"https://www.mollie.com/checkout/tr_reel1"}}}
                """.formatted(statut);
    }

    @Nested
    @DisplayName("Creation d un paiement")
    class Creation {

        @Test
        @DisplayName("un jeton d organisation transmet profileId et testmode")
        void jetonOrganisation() {
            serveur.expect(requestTo(API + "/payments"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    // Sans profileId, Mollie refuse : un jeton d organisation ne
                    // designe aucun profil de site.
                    .andExpect(jsonPath("$.profileId").value("pfl_demo"))
                    .andExpect(jsonPath("$.testmode").value(true))
                    .andExpect(jsonPath("$.amount.value").value("80.21"))
                    .andExpect(jsonPath("$.amount.currency").value("EUR"))
                    .andExpect(jsonPath("$.description").value("Commande CMD-2026-0001"))
                    .andExpect(jsonPath("$.redirectUrl")
                            .value("https://garage.example/commande/abc/retour"))
                    // La cle d idempotence empeche un rejeu de debiter deux fois.
                    .andExpect(header("Idempotency-Key", "cle-idempotence-1"))
                    .andRespond(withSuccess(paiementJson("open"), MediaType.APPLICATION_JSON));

            PaiementCree cree = passerelleAvecJetonOrganisation()
                    .creerPaiement(demande("https://garage.example/webhooks/paiement"));

            assertThat(cree.referencePrestataire()).isEqualTo("tr_reel1");
            assertThat(cree.urlRedirection())
                    .isEqualTo("https://www.mollie.com/checkout/tr_reel1");
            serveur.verify();
        }

        @Test
        @DisplayName("une cle API ne transmet ni profileId ni testmode")
        void cleApi() {
            // Mollie refuse ces champs avec une cle API, qui porte deja profil et mode.
            serveur.expect(requestTo(API + "/payments"))
                    .andExpect(jsonPath("$.profileId").doesNotExist())
                    .andExpect(jsonPath("$.testmode").doesNotExist())
                    .andRespond(withSuccess(paiementJson("open"), MediaType.APPLICATION_JSON));

            passerelleAvecCleApi().creerPaiement(demande("https://garage.example/webhooks/paiement"));
            serveur.verify();
        }

        @Test
        @DisplayName("une URL de notification publique est transmise")
        void notificationPublique() {
            serveur.expect(requestTo(API + "/payments"))
                    .andExpect(jsonPath("$.webhookUrl")
                            .value("https://garage.example/webhooks/paiement"))
                    .andRespond(withSuccess(paiementJson("open"), MediaType.APPLICATION_JSON));

            passerelleAvecJetonOrganisation()
                    .creerPaiement(demande("https://garage.example/webhooks/paiement"));
            serveur.verify();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "http://localhost:8080/webhooks/paiement",
                "http://127.0.0.1:8080/webhooks/paiement",
                "http://poste-dev/webhooks/paiement",
                "http://garage.test/webhooks/paiement"})
        @DisplayName("une URL que Mollie ne peut pas joindre est OMISE, pas envoyee")
        void notificationLocaleOmise(String urlLocale) {
            // La transmettre ferait rejeter la creation du paiement par Mollie : tout
            // le parcours echouerait en developpement, la ou rien d autre ne cloche.
            // Omise, le statut sera constate au retour du membre.
            serveur.expect(requestTo(API + "/payments"))
                    .andExpect(jsonPath("$.webhookUrl").doesNotExist())
                    .andRespond(withSuccess(paiementJson("open"), MediaType.APPLICATION_JSON));

            passerelleAvecJetonOrganisation().creerPaiement(demande(urlLocale));
            serveur.verify();
        }

        @Test
        @DisplayName("un montant a une decimale est envoye avec deux et un point")
        void formatDuMontant() {
            // Un montant formate a la belge (« 80,2 ») serait rejete ou mal interprete.
            serveur.expect(requestTo(API + "/payments"))
                    .andExpect(jsonPath("$.amount.value").value("80.20"))
                    .andRespond(withSuccess("""
                            {"id":"tr_x","status":"open",
                             "amount":{"currency":"EUR","value":"80.20"},
                             "_links":{"checkout":{"href":"https://mollie/x"}}}
                            """, MediaType.APPLICATION_JSON));

            passerelleAvecJetonOrganisation().creerPaiement(new DemandePaiement(
                    "CMD-1", new BigDecimal("80.2"), "EUR", "cle", "https://g/r", null));
            serveur.verify();
        }

        @Test
        @DisplayName("un montant confirme different du montant demande est refuse")
        void montantDivergent() {
            // Poser sur la commande une reference qui encaisserait autre chose que son
            // du serait pire qu un echec : le membre paierait un montant qu il n a pas
            // valide.
            serveur.expect(requestTo(API + "/payments"))
                    .andRespond(withSuccess("""
                            {"id":"tr_x","status":"open",
                             "amount":{"currency":"EUR","value":"8.02"},
                             "_links":{"checkout":{"href":"https://mollie/x"}}}
                            """, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation()
                    .creerPaiement(demande(null)))
                    .isInstanceOf(PrestataireIndisponibleException.class)
                    .hasMessageContaining("Montant");
        }

        @Test
        @DisplayName("une reponse sans URL de paiement est refusee")
        void reponseIncomplete() {
            serveur.expect(requestTo(API + "/payments"))
                    .andRespond(withSuccess("""
                            {"id":"tr_x","status":"open",
                             "amount":{"currency":"EUR","value":"80.21"}}
                            """, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation()
                    .creerPaiement(demande(null)))
                    .isInstanceOf(PrestataireIndisponibleException.class);
        }
    }

    @Nested
    @DisplayName("Relecture du statut")
    class Relecture {

        @ParameterizedTest(name = "{0} projette vers {1}")
        @CsvSource({
                "open, INITIE",
                "pending, EN_COURS",
                "authorized, EN_COURS",
                "paid, REUSSI",
                "failed, ECHOUE",
                "canceled, ECHOUE",
                "expired, EXPIRE"})
        @DisplayName("les statuts Mollie sont projetes vers le vocabulaire du domaine")
        void projection(String statutMollie, StatutPaiement attendu) {
            serveur.expect(requestTo(API + "/payments/tr_reel1?testmode=true"))
                    .andRespond(withSuccess(paiementJson(statutMollie),
                            MediaType.APPLICATION_JSON));

            assertThat(passerelleAvecJetonOrganisation().lireStatut("tr_reel1"))
                    .isEqualTo(attendu);
            serveur.verify();
        }

        @Test
        @DisplayName("authorized n est PAS un encaissement")
        void autorisationNEstPasEncaissement() {
            // L autorisation reserve les fonds sans les capturer. La traiter comme un
            // paiement decrementerait le stock et emettrait une facture pour un
            // montant qui peut encore ne jamais arriver.
            serveur.expect(requestTo(API + "/payments/tr_reel1?testmode=true"))
                    .andRespond(withSuccess(paiementJson("authorized"),
                            MediaType.APPLICATION_JSON));

            assertThat(passerelleAvecJetonOrganisation().lireStatut("tr_reel1"))
                    .isNotEqualTo(StatutPaiement.REUSSI);
        }

        @Test
        @DisplayName("une cle API n ajoute pas testmode a l URL")
        void sansTestmodeAvecCleApi() {
            serveur.expect(requestTo(API + "/payments/tr_reel1"))
                    .andRespond(withSuccess(paiementJson("paid"), MediaType.APPLICATION_JSON));

            assertThat(passerelleAvecCleApi().lireStatut("tr_reel1"))
                    .isEqualTo(StatutPaiement.REUSSI);
            serveur.verify();
        }

        @Test
        @DisplayName("un statut inconnu leve plutot que de deviner")
        void statutInconnu() {
            // Se rabattre sur ECHOUE annulerait peut-etre un paiement reussi ; sur
            // REUSSI, livrerait sans encaissement. L incertitude doit remonter.
            serveur.expect(requestTo(API + "/payments/tr_reel1?testmode=true"))
                    .andRespond(withSuccess(paiementJson("chargeback_pending"),
                            MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation().lireStatut("tr_reel1"))
                    .isInstanceOf(PrestataireIndisponibleException.class)
                    .hasMessageContaining("chargeback_pending");
        }
    }

    @Nested
    @DisplayName("Remboursement")
    class Remboursement {

        private DemandeRemboursement demandeRemboursement() {
            return new DemandeRemboursement("tr_reel1", new BigDecimal("80.21"), "EUR",
                    "remboursement-uuid-stable");
        }

        @Test
        @DisplayName("le Refund porte la cle derivee du paiement et pas de profileId")
        void refund() {
            serveur.expect(requestTo(API + "/payments/tr_reel1/refunds"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(jsonPath("$.amount.value").value("80.21"))
                    .andExpect(jsonPath("$.testmode").value(true))
                    // Le Refund se rattache au paiement d origine, qui porte deja son
                    // profil : le repeter serait au mieux redondant.
                    .andExpect(jsonPath("$.profileId").doesNotExist())
                    // Cle DERIVEE du paiement, donc stable au rejeu.
                    .andExpect(header("Idempotency-Key", "remboursement-uuid-stable"))
                    .andRespond(withSuccess("""
                            {"id":"re_1","status":"pending",
                             "amount":{"currency":"EUR","value":"80.21"}}
                            """, MediaType.APPLICATION_JSON));

            assertThat(passerelleAvecJetonOrganisation().rembourser(demandeRemboursement())
                    .referenceRemboursement()).isEqualTo("re_1");
            serveur.verify();
        }

        @Test
        @DisplayName("un montant rembourse divergent est refuse")
        void montantDivergent() {
            serveur.expect(requestTo(API + "/payments/tr_reel1/refunds"))
                    .andRespond(withSuccess("""
                            {"id":"re_1","status":"pending",
                             "amount":{"currency":"EUR","value":"8.02"}}
                            """, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation()
                    .rembourser(demandeRemboursement()))
                    .isInstanceOf(PrestataireIndisponibleException.class);
        }
    }

    @Nested
    @DisplayName("Panne du prestataire")
    class Panne {

        @Test
        @DisplayName("une erreur HTTP devient PrestataireIndisponibleException, jamais brute")
        void erreurHttp() {
            // Aucune exception du client HTTP ne doit franchir la passerelle : le web
            // ne saurait pas la traduire, et elle finirait en 500 au milieu d un achat.
            serveur.expect(requestTo(API + "/payments")).andRespond(withServerError());

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation()
                    .creerPaiement(demande(null)))
                    .isInstanceOf(PrestataireIndisponibleException.class)
                    .hasMessageContaining("creation du paiement");
        }

        @Test
        @DisplayName("le message d erreur ne divulgue pas la reponse du prestataire")
        void reponseNonDivulguee() {
            // Une reponse de prestataire peut porter des identifiants de requete et des
            // details d infrastructure : ils n ont rien a faire dans un message qui
            // remonte la pile.
            serveur.expect(requestTo(API + "/payments"))
                    .andRespond(withServerError()
                            .body("{\"detail\":\"identifiant-interne-confidentiel\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation()
                    .creerPaiement(demande(null)))
                    .isInstanceOf(PrestataireIndisponibleException.class)
                    .hasMessageNotContaining("identifiant-interne-confidentiel");
        }

        @Test
        @DisplayName("une reponse 4xx est traitee comme une panne, pas comme un succes")
        void erreurClient() {
            serveur.expect(requestTo(API + "/payments"))
                    .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

            assertThatThrownBy(() -> passerelleAvecJetonOrganisation()
                    .creerPaiement(demande(null)))
                    .isInstanceOf(PrestataireIndisponibleException.class);
        }
    }
}
