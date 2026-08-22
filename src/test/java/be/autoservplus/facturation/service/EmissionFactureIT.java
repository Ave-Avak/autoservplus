package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Emission de la facture au bout de la chaine reelle : panier, commande, paiement,
 * webhook — puis la facture, produite par le listener AFTER_COMMIT (F31).
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} de classe, comme
 * {@code NotificationInterventionIT} : le contrat teste EST le commit. Une
 * transaction englobante rollbackee empecherait le listener de se declencher et ce
 * fichier ne prouverait rien. Consequence : les donnees restent en base du conteneur
 * (jetable, un par classe), d ou les references uniques par test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Emission de la facture apres paiement (integration)")
class EmissionFactureIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Sans rollback, chaque test laisse ses donnees : les references doivent differer. */
    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private FactureRepository factures;
    @Autowired private FactureService factureService;
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
            categorie = categories.findByCode("IT-FAC-CAT")
                    .orElseGet(() -> categories.save(
                            new Categorie("IT-FAC-CAT", "Pieces", TypeCategorie.PIECE)));
        });
    }

    private Piece piece(String prixHtva, String tauxTva) {
        return transactions.execute(statut -> {
            Piece piece = new Piece(categorie, "IT-FAC-" + COMPTEUR.getAndIncrement(),
                    "Piece de test", new BigDecimal(prixHtva));
            piece.setTauxTva(new BigDecimal(tauxTva));
            piece.setQuantiteStock(50);
            return pieces.save(piece);
        });
    }

    /** Panier converti en commande ; retourne la reference de la commande. */
    private UUID commander(Piece piece, int quantite) throws Exception {
        panierService.ajouterPiece("marie@exemple.be", piece.getReference(), quantite);
        String redirection = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection.replace("/commande/", "").replace("/confirmation", ""));
    }

    /** Paie la commande jusqu au webhook confirme : c est le commit qui declenche tout. */
    private void payer(UUID referenceCommande) throws Exception {
        String redirection = mvc.perform(
                        post("/commande/{ref}/payer", referenceCommande).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = redirection.substring(redirection.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        notifier(referencePrestataire);
    }

    private void notifier(String referencePrestataire) throws Exception {
        mvc.perform(post("/webhooks/paiement").with(anonymous())
                .param("id", referencePrestataire)).andExpect(status().isOk());
    }

    /** Facture d une commande, lue dans sa propre transaction (la classe n en a pas). */
    private Facture factureDe(UUID referenceCommande) {
        return transactions.execute(statut ->
                factureService.factureDe(referenceCommande).orElseThrow());
    }

    private String referencePrestataireDe(UUID referenceCommande) {
        return jdbc.queryForObject("""
                SELECT p.reference_mollie FROM paiement p
                JOIN commande c ON c.id = p.commande_id
                WHERE c.reference = ?
                """, String.class, referenceCommande);
    }

    @Test
    @DisplayName("le paiement confirme fait naitre la facture, numerotee et montants figes")
    void factureEmiseApresLeCommit() throws Exception {
        Piece plaquettes = piece("19.99", "21.00");
        UUID referenceCommande = commander(plaquettes, 2);

        payer(referenceCommande);

        Facture facture = factureDe(referenceCommande);
        assertThat(facture.getNumero()).matches("2\\d{3}-\\d{4}");
        assertThat(facture.getMontantHtva()).isEqualByComparingTo("39.98");
        assertThat(facture.getMontantTva()).isEqualByComparingTo("8.40");
        assertThat(facture.getMontantTvac()).isEqualByComparingTo("48.38");
        assertThat(facture.getTauxTvaApplique()).isEqualByComparingTo("21.00");
        assertThat(facture.getCheminPdf())
                .as("Aucun PDF a l emission : il sera fabrique a la premiere demande")
                .isNull();
        assertThat(commandes.findByReference(referenceCommande).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.PAYEE);
    }

    @Test
    @DisplayName("webhook rejoue : une seule facture, un seul numero consomme")
    void idempotenceSurRejeuDuWebhook() throws Exception {
        Piece piece = piece("10.00", "21.00");
        UUID referenceCommande = commander(piece, 1);
        payer(referenceCommande);
        String numeroInitial = factureDe(referenceCommande).getNumero();

        // Trois rejeux du meme webhook, comme un prestataire qui reessaie.
        String referencePrestataire = referencePrestataireDe(referenceCommande);
        notifier(referencePrestataire);
        notifier(referencePrestataire);
        notifier(referencePrestataire);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM facture f
                JOIN commande c ON c.id = f.commande_id
                WHERE c.reference = ?
                """, Integer.class, referenceCommande))
                .as("Une commande, au plus une facture")
                .isEqualTo(1);
        assertThat(factureDe(referenceCommande).getNumero()).isEqualTo(numeroInitial);
    }

    @Test
    @DisplayName("modifier le catalogue apres coup ne reecrit pas la facture emise")
    void montantsFigesApresEmission() throws Exception {
        Piece piece = piece("100.00", "21.00");
        UUID referenceCommande = commander(piece, 1);
        payer(referenceCommande);

        // Le garage double son prix apres l encaissement.
        transactions.executeWithoutResult(statut -> {
            Piece rechargee = pieces.findByReference(piece.getReference()).orElseThrow();
            rechargee.modifierPrix(new BigDecimal("200.00"));
            rechargee.renommer("Piece renommee");
            pieces.saveAndFlush(rechargee);
        });

        Facture facture = factureDe(referenceCommande);
        assertThat(facture.getMontantHtva())
                .as("La facture porte le prix encaisse, pas le catalogue du jour")
                .isEqualByComparingTo("100.00");
        assertThat(facture.getMontantTvac()).isEqualByComparingTo("121.00");
        // La ligne facturee garde aussi son libelle fige.
        assertThat(jdbc.queryForObject("""
                SELECT l.libelle_fige FROM ligne_panier l
                JOIN commande c ON c.id = l.commande_id
                WHERE c.reference = ?
                """, String.class, referenceCommande))
                .isEqualTo("Piece de test");
    }

    @Test
    @DisplayName("taux mixtes : le taux unique est NULL, les montants restent coherents")
    void factureMultiTaux() throws Exception {
        Piece standard = piece("100.00", "21.00");
        Piece reduit = piece("50.00", "6.00");
        panierService.ajouterPiece("marie@exemple.be", standard.getReference(), 1);
        UUID referenceCommande = commander(reduit, 1);

        payer(referenceCommande);

        Facture facture = factureDe(referenceCommande);
        assertThat(facture.getTauxTvaApplique())
                .as("Aucun taux unique n est vrai sur une facture multi-taux")
                .isNull();
        assertThat(facture.getMontantHtva()).isEqualByComparingTo("150.00");
        assertThat(facture.getMontantTva()).isEqualByComparingTo("24.00");
        assertThat(facture.getMontantTvac()).isEqualByComparingTo("174.00");

        VentilationTva ventilation = transactions.execute(statut ->
                factureService.ventilationDe(
                        factures.findByReference(facture.getReference()).orElseThrow()));
        assertThat(ventilation.tranches())
                .extracting(v -> v.taux().toPlainString(), v -> v.montantTva().toPlainString())
                .containsExactly(tuple6(), tuple21());
    }

    private static org.assertj.core.groups.Tuple tuple6() {
        return org.assertj.core.groups.Tuple.tuple("6", "3.00");
    }

    private static org.assertj.core.groups.Tuple tuple21() {
        return org.assertj.core.groups.Tuple.tuple("21", "21.00");
    }
}
