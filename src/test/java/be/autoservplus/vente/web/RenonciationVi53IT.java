package be.autoservplus.vente.web;

import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renonciation VI.53 (F12-a), sur un PostgreSQL reel.
 *
 * <p>Verifie ce qui ne se voit qu en base : les <b>deux</b> ecritures de V31 — l etat
 * sur {@code commande.renonciation_vi53} et la preuve dans {@code consentement} — et
 * le fait que le refus s ecrit autant que l accord.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Renonciation VI.53 (integration)")
class RenonciationVi53IT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private PanierService paniers;
    @Autowired private CommandeService commandes;
    @Autowired private CommandeRepository commandeRepository;
    @Autowired private PrestationRepository prestations;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private JdbcTemplate jdbc;

    private String membre;

    @BeforeEach
    void setUp() {
        membre = "vi53-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        utilisateurs.save(new Utilisateur(membre, "$2a$12$abcdefghijklmnopqrstuv",
                "Test", "Alex", TypeUtilisateur.MEMBRE));
    }

    /**
     * Le seed ne cree AUCUNE piece (V16 ne seede que des prestations). Les cas
     * « panier de pieces » doivent donc fabriquer la leur : un test qui se sauterait
     * faute de donnee ne prouverait rien.
     */
    private UUID unePiece() {
        jdbc.update("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva,
                                   quantite_stock)
                VALUES ((SELECT id FROM categorie WHERE type = 'PIECE' LIMIT 1), ?, ?, 9.00, 10)
                """, "REF-VI53-" + membre, "Piece de test VI53");
        return jdbc.queryForObject("SELECT reference FROM piece WHERE reference_fabricant = ?",
                UUID.class, "REF-VI53-" + membre);
    }

    private UUID unePrestation() {
        return prestations.findByActifTrueOrderByLibelleAsc().get(0).getReference();
    }

    private long nombreDePreuves(boolean accorde) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM consentement c
                JOIN utilisateur u ON u.id = c.utilisateur_id
                WHERE u.email = ? AND c.type_document = ? AND c.accorde = ?
                """, Long.class, membre,
                TypeDocumentConsentement.RENONCIATION_RETRACTATION.name(), accorde);
    }

    @Nested
    @DisplayName("Etat et preuve, dans la meme transaction")
    class EtatEtPreuve {

        @Test
        @WithMockUser
        @DisplayName("case cochee : colonne a true ET preuve accorde=true")
        void renonciationConsentie() {
            paniers.ajouterService(membre, unePrestation(), 1);

            var confirmation = commandes.passerCommande(membre, true, true, "127.0.0.1");

            var commande = commandeRepository.findByReference(confirmation.reference())
                    .orElseThrow();
            assertThat(commande.isRenonciationVi53())
                    .as("c est l ETAT que F30 lira pour decider")
                    .isTrue();
            assertThat(nombreDePreuves(true))
                    .as("et la PREUVE horodatee vit dans consentement, comme les CGV")
                    .isEqualTo(1);
        }

        @Test
        @WithMockUser
        @DisplayName("case non cochee : colonne a false ET preuve accorde=false")
        void refusEnregistreAussi() {
            paniers.ajouterService(membre, unePrestation(), 1);

            var confirmation = commandes.passerCommande(membre, true, false, "127.0.0.1");

            var commande = commandeRepository.findByReference(confirmation.reference())
                    .orElseThrow();
            assertThat(commande.isRenonciationVi53()).isFalse();
            assertThat(nombreDePreuves(false))
                    .as("sans ligne, l absence serait ambigue entre « a refuse » et "
                            + "« jamais interroge »")
                    .isEqualTo(1);
        }

        @Test
        @WithMockUser
        @DisplayName("les deux ecritures vont ensemble : jamais d etat sans preuve")
        void atomicite() {
            paniers.ajouterService(membre, unePrestation(), 1);
            commandes.passerCommande(membre, true, true, "127.0.0.1");

            long etats = jdbc.queryForObject("""
                    SELECT count(*) FROM commande c
                    JOIN utilisateur u ON u.id = c.membre_id
                    WHERE u.email = ? AND c.renonciation_vi53 = true
                    """, Long.class, membre);

            assertThat(etats).isEqualTo(nombreDePreuves(true));
        }
    }

    @Nested
    @DisplayName("Validation serveur")
    class ValidationServeur {

        @Test
        @WithMockUser
        @DisplayName("une case cochee sur un panier de PIECES est ignoree")
        void panierDePiecesIgnoreLaCase() {
            paniers.ajouterPiece(membre, unePiece(), 1);

            var confirmation = commandes.passerCommande(membre, true, true, "127.0.0.1");

            assertThat(commandeRepository.findByReference(confirmation.reference())
                    .orElseThrow().isRenonciationVi53())
                    .as("jamais confiance au client : la renonciation n a de sens que "
                            + "pour un service")
                    .isFalse();
            assertThat(nombreDePreuves(true))
                    .as("et aucune preuve n est ecrite pour une question jamais posee")
                    .isZero();
        }

        @Test
        @WithMockUser
        @DisplayName("aucune preuve n est ecrite pour un panier de pieces")
        void aucunePreuveSansService() {
            paniers.ajouterPiece(membre, unePiece(), 1);
            commandes.passerCommande(membre, true, false, "127.0.0.1");

            assertThat(nombreDePreuves(false)).isZero();
        }
    }

    @Nested
    @DisplayName("Presentation de la case")
    class Presentation {

        @Test
        @WithMockUser
        @DisplayName("presentee sur un recapitulatif de services, et NON pre-cochee")
        void presenteeSurUnPanierDeServices() throws Exception {
            paniers.ajouterService(membre, unePrestation(), 1);

            mvc.perform(get("/commande").with(user(membre)))
                    .andExpect(status().isOk())
                    // Assertion PRECISE sur la balise elle-meme : une case pre-cochee
                    // vaudrait consentement implicite, et chercher « checked » n importe
                    // ou dans la page rendrait le test dependant du reste du gabarit.
                    .andExpect(content().string(containsString(
                            "name=\"renonciationVi53\" value=\"true\">")));
        }

        @Test
        @WithMockUser
        @DisplayName("absente d un recapitulatif de pieces")
        void absenteSurUnPanierDePieces() throws Exception {
            paniers.ajouterPiece(membre, unePiece(), 1);

            mvc.perform(get("/commande").with(user(membre)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("renonciationVi53"))));
        }
    }
}
