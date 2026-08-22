package be.autoservplus.facturation.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Telechargement de la facture de bout en bout (F31) : de l achat au PDF servi,
 * avec archivage reel sur disque et appartenance verifiee.
 *
 * <p>Sans {@code @Transactional} de classe : la facture nait d un listener
 * AFTER_COMMIT, une transaction englobante rollbackee l empecherait d exister.
 * L archive pointe sur un repertoire temporaire, injecte par
 * {@code @DynamicPropertySource} — un test ne doit rien ecrire dans l arborescence
 * du projet.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Telechargement de la facture (integration)")
class TelechargementFactureIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    static Path archive;

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @DynamicPropertySource
    static void archiveTemporaire(DynamicPropertyRegistry registre) {
        registre.add("autoservplus.facturation.archive", () -> archive.toString());
    }

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private FactureService factures;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    private Categorie categorie;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(statut -> {
            utilisateurs.findByEmailIgnoreCase("marie@exemple.be")
                    .orElseGet(() -> utilisateurs.save(new Utilisateur(
                            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                            TypeUtilisateur.MEMBRE)));
            utilisateurs.findByEmailIgnoreCase("paul@exemple.be")
                    .orElseGet(() -> utilisateurs.save(new Utilisateur(
                            "paul@exemple.be", "$2a$12$h", "Martin", "Paul",
                            TypeUtilisateur.MEMBRE)));
            categorie = categories.findByCode("IT-DL-CAT")
                    .orElseGet(() -> categories.save(
                            new Categorie("IT-DL-CAT", "Pieces", TypeCategorie.PIECE)));
        });
    }

    /** Achete et paie une piece ; retourne la facture emise. */
    private Facture acheterEtPayer() throws Exception {
        Piece piece = transactions.execute(statut -> {
            Piece nouvelle = new Piece(categorie, "IT-DL-" + COMPTEUR.getAndIncrement(),
                    "Plaquettes de frein", new BigDecimal("19.99"));
            nouvelle.setTauxTva(new BigDecimal("21.00"));
            nouvelle.setQuantiteStock(20);
            return pieces.save(nouvelle);
        });
        panierService.ajouterPiece("marie@exemple.be", piece.getReference(), 2);
        String redirection = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID referenceCommande = UUID.fromString(
                redirection.replace("/commande/", "").replace("/confirmation", ""));

        String paiement = mvc.perform(post("/commande/{ref}/payer", referenceCommande).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous())
                .param("id", referencePrestataire)).andExpect(status().isOk());

        return transactions.execute(statut ->
                factures.factureDe(referenceCommande).orElseThrow());
    }

    @Test
    @DisplayName("le membre telecharge sa facture : un vrai PDF, nomme d'apres son numero")
    void telechargementDuPdf() throws Exception {
        Facture facture = acheterEtPayer();

        byte[] pdf = mvc.perform(get("/factures/{ref}/pdf", facture.getReference()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        containsString("facture-" + facture.getNumero() + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("premiere demande : le fichier est archive et son chemin inscrit en base")
    void premiereDemandeArchive() throws Exception {
        Facture facture = acheterEtPayer();
        assertThat(cheminEnBase(facture))
                .as("Rien n est archive tant que personne n a demande le document")
                .isNull();

        mvc.perform(get("/factures/{ref}/pdf", facture.getReference()))
                .andExpect(status().isOk());

        // L exercice se lit sur le numero lui-meme : l archive suit la facture,
        // le test ne suppose pas l annee courante.
        String chemin = cheminEnBase(facture);
        assertThat(chemin).isEqualTo("%s/%s.pdf".formatted(
                facture.getNumero().substring(0, 4), facture.getNumero()));
        assertThat(archive.resolve(chemin)).exists();
    }

    @Test
    @DisplayName("seconde demande : l'archive est servie telle quelle, rien n'est regenere")
    void secondeDemandeSertLArchive() throws Exception {
        Facture facture = acheterEtPayer();
        mvc.perform(get("/factures/{ref}/pdf", facture.getReference())).andExpect(status().isOk());

        // Marqueur glisse dans le fichier archive : s il ressort au second appel,
        // c est bien le fichier conserve qui est servi et non un PDF refabrique.
        Path fichier = archive.resolve(cheminEnBase(facture));
        byte[] marque = "%PDF-1.4 archive temoin".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(fichier, marque);

        byte[] servi = mvc.perform(get("/factures/{ref}/pdf", facture.getReference()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(servi).isEqualTo(marque);
    }

    @Test
    @WithMockUser(username = "paul@exemple.be")
    @DisplayName("la facture d'un autre membre repond 404")
    void factureDAutrui() throws Exception {
        Facture deMarie = transactions.execute(statut -> factureDeMarie());

        mvc.perform(get("/factures/{ref}/pdf", deMarie.getReference()))
                .andExpect(status().isNotFound());
    }

    /**
     * Paul est le principal du test : la facture de Marie est fabriquee directement
     * en base, l achat passant par des endpoints qui prendraient l identite courante.
     */
    private Facture factureDeMarie() {
        Utilisateur marie = utilisateurs.findByEmailIgnoreCase("marie@exemple.be").orElseThrow();
        jdbc.update("""
                INSERT INTO commande (reference, numero, membre_id, statut, montant_htva,
                                      montant_tva, montant_tvac, date_commande, date_paiement)
                VALUES (?, ?, ?, 'PAYEE', 10.00, 2.10, 12.10, now(), now())
                """, UUID.randomUUID(), "CMD-IT-DL-" + COMPTEUR.getAndIncrement(), marie.getId());
        UUID reference = UUID.fromString(jdbc.queryForObject(
                "SELECT reference::text FROM commande ORDER BY id DESC LIMIT 1", String.class));
        return factures.emettrePourCommande(reference);
    }

    private String cheminEnBase(Facture facture) {
        return jdbc.queryForObject("SELECT chemin_pdf FROM facture WHERE reference = ?",
                String.class, facture.getReference());
    }
}
