package be.autoservplus.legal.web;

import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notice harmonisée sur la garantie légale (VI.45 § 1er, 12° CDE) : la page publique, et
 * la phrase de lien qui y mène depuis le parcours d'achat d'une pièce, jamais depuis
 * celui d'une prestation seule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Garantie légale : page et liens (intégration)")
class GarantieLegaleIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    /**
     * La phrase de lien du parcours d'achat, et non le lien « Garantie légale » de la ligne
     * légale, présent au pied de toutes les pages.
     */
    private static final String LIEN =
            "<a href=\"/garantie-legale\">Vos droits de garantie légale</a>";

    @Autowired private MockMvc mvc;
    @Autowired private PanierService paniers;
    @Autowired private CommandeService commandes;
    @Autowired private PrestationRepository prestations;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private JdbcTemplate jdbc;

    private String membre;

    @BeforeEach
    void setUp() {
        membre = "garantie-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        utilisateurs.save(new Utilisateur(membre, "$2a$12$abcdefghijklmnopqrstuv",
                "Test", "Alex", TypeUtilisateur.MEMBRE));
    }

    /** La graine ne crée aucune pièce : chaque cas fabrique la sienne. */
    private UUID unePiece() {
        jdbc.update("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva,
                                   quantite_stock)
                VALUES ((SELECT id FROM categorie WHERE type = 'PIECE' LIMIT 1), ?, ?, 9.00, 10)
                """, "REF-GAR-" + membre, "Pièce de test garantie");
        return jdbc.queryForObject("SELECT reference FROM piece WHERE reference_fabricant = ?",
                UUID.class, "REF-GAR-" + membre);
    }

    private UUID unePrestation() {
        return prestations.findByActifTrueOrderByLibelleAsc().get(0).getReference();
    }

    /**
     * Appel direct d'un service au nom du membre. MockMvc vide le contexte de sécurité
     * après chaque requête : l'authentification est posée ici, pour cet appel seul.
     */
    private <T> T auNomDuMembre(Supplier<T> appel) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(membre, null, "ROLE_MEMBRE"));
        try {
            return appel.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID commander() {
        return auNomDuMembre(() -> commandes.passerCommande(membre, true, false, "127.0.0.1").reference());
    }

    private String page(String adresse, boolean connecte) throws Exception {
        var requete = get(adresse).header("Accept-Language", "fr");
        requete = connecte ? requete.with(user(membre).roles("MEMBRE")) : requete.with(anonymous());
        return mvc.perform(requete).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Nested
    @DisplayName("Page /garantie-legale")
    class Page {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "fr, Garantie légale, https://europa.eu/youreurope/garanties",
                "nl, Wettelijke garantie, https://europa.eu/youreurope/garantie",
                "en, Legal guarantee, https://europa.eu/youreurope/guarantees"})
        @DisplayName("publique, traduite, avec le lien Your Europe de la langue")
        void pageTraduite(String langue, String titre, String youreurope) throws Exception {
            String page = mvc.perform(get("/garantie-legale").param("lang", langue).with(anonymous()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(page)
                    .contains("<title>" + titre + " | AutoServ+</title>")
                    .contains("href=\"" + youreurope + "\"");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "fr, Notice harmonisée de l'Union européenne sur la garantie légale de conformité, Que faire",
                "nl, Geharmoniseerde kennisgeving van de Europese Unie over de wettelijke conformiteitsgarantie, Wat moet u doen",
                "en, Harmonised European Union notice on the legal guarantee of conformity, What to do"})
        @DisplayName("notice de la langue en image, avec sa transcription")
        void noticeEtTranscription(String langue, String alt, String quefaire) throws Exception {
            String page = mvc.perform(get("/garantie-legale").param("lang", langue).with(anonymous()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(page)
                    .contains("<img src=\"/images/notice-garantie/notice-garantie-" + langue + ".svg\"")
                    .contains("alt=\"" + alt.replace("'", "&#39;") + "\"")
                    .contains("<summary>")
                    .contains(">" + quefaire + "</h3>")
                    .doesNotContain("<svg");
        }

        /**
         * Le SVG anglais porte son code QR en PNG {@code data:}. Servi comme image, il
         * reçoit la politique de sécurité du site : elle doit admettre {@code data:} en
         * {@code img-src}, sans quoi le code QR anglais disparaîtrait.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource({"fr", "nl", "en"})
        @DisplayName("SVG servi en image/svg+xml, nosniff, CSP qui admet data: en img-src")
        void enTetesDuSvg(String langue) throws Exception {
            var reponse = mvc.perform(get("/images/notice-garantie/notice-garantie-" + langue + ".svg")
                            .with(anonymous()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse();

            assertThat(reponse.getContentType()).startsWith("image/svg+xml");
            assertThat(reponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(reponse.getHeader("Content-Security-Policy")).contains("img-src 'self' data:");
        }
    }

    @Nested
    @DisplayName("Catalogue")
    class Catalogue {

        @Test
        @DisplayName("le catalogue des pièces renvoie à la notice, celui des prestations non")
        void listes() throws Exception {
            unePiece();

            assertThat(page("/pieces", false)).contains(LIEN);
            assertThat(page("/services", false)).doesNotContain(LIEN);
        }

        @Test
        @DisplayName("la fiche d'une pièce renvoie à la notice, celle d'une prestation non")
        void fiches() throws Exception {
            assertThat(page("/pieces/" + unePiece(), false)).contains(LIEN);
            assertThat(page("/services/" + unePrestation(), false)).doesNotContain(LIEN);
        }
    }

    @Nested
    @DisplayName("Commande")
    class Commande {

        @Test
        @DisplayName("panier avec une pièce : lien au récapitulatif, à la confirmation et au détail")
        void avecUnePiece() throws Exception {
            UUID piece = unePiece();
            auNomDuMembre(() -> paniers.ajouterPiece(membre, piece, 1));

            assertThat(page("/commande", true)).contains(LIEN);

            UUID reference = commander();
            assertThat(page("/commande/" + reference + "/confirmation", true)).contains(LIEN);
            assertThat(page("/commandes/" + reference, true)).contains(LIEN);
        }

        @Test
        @DisplayName("prestations seules : aucun lien, ni au récapitulatif, ni après la commande")
        void prestationsSeules() throws Exception {
            auNomDuMembre(() -> paniers.ajouterService(membre, unePrestation(), 1));

            assertThat(page("/commande", true)).doesNotContain(LIEN);

            UUID reference = commander();
            assertThat(page("/commande/" + reference + "/confirmation", true)).doesNotContain(LIEN);
            assertThat(page("/commandes/" + reference, true)).doesNotContain(LIEN);
        }
    }
}
