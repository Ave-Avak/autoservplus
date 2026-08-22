package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaine de paiement de bout en bout contre le BOUCHON : conversion, initiation
 * (URL du prestataire), webhook anonyme sans CSRF traite via la relecture du
 * statut, decrement du stock, idempotence du rejeu. Aucun appel Mollie reel —
 * la passerelle de production n est active qu en profil prod.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Paiement de bout en bout (integration, bouchon)")
class PaiementIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private PaiementRepository paiements;
    @Autowired private PrestatairePaiementFictif prestataireFictif;

    private Piece plaquettes;
    private Piece ampoule;

    @BeforeEach
    void setUp() {
        utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Categorie freinage = categories.save(
                new Categorie("IT-PAY-FRE", "Freinage", TypeCategorie.PIECE));
        plaquettes = new Piece(freinage, "IT-PAY-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
        plaquettes = pieces.save(plaquettes);
        ampoule = new Piece(freinage, "IT-PAY-002", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(5);
        ampoule = pieces.save(ampoule);
    }

    /** Panier RM-30 du module converti en commande ; retourne la reference de la commande. */
    private UUID commander() throws Exception {
        panierService.ajouterPiece("marie@exemple.be", ampoule.getReference(), 3);
        panierService.ajouterPiece("marie@exemple.be", plaquettes.getReference(), 2);
        String redirection = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection
                .replace("/commande/", "").replace("/confirmation", ""));
    }

    /** Initie le paiement et retourne la reference attribuee par le prestataire fictif. */
    private String initierPaiement(UUID referenceCommande) throws Exception {
        String redirection = mvc.perform(
                        post("/commande/{ref}/payer", referenceCommande).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        return redirection.substring(redirection.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("paye : webhook anonyme sans CSRF, statut relu, commande PAYEE, stock decremente, rejeu sans double effet")
    void parcoursPayeDeBoutEnBout() throws Exception {
        UUID referenceCommande = commander();
        String referencePrestataire = initierPaiement(referenceCommande);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);

        // Le prestataire appelle sans session ni jeton : c est la configuration
        // reelle de securite qui laisse passer, et la RELECTURE du statut qui decide.
        mvc.perform(post("/webhooks/paiement").with(anonymous())
                        .param("id", referencePrestataire))
                .andExpect(status().isOk());

        Commande commande = commandes.findByReference(referenceCommande).orElseThrow();
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(commande.getDatePaiement()).isNotNull();
        assertThat(commande.isRuptureAHonorer()).isFalse();
        Paiement paiement = paiements.findByReferenceMollie(referencePrestataire).orElseThrow();
        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REUSSI);
        assertThat(plaquettes.getQuantiteStock()).isEqualTo(8);
        assertThat(ampoule.getQuantiteStock()).isEqualTo(2);

        // Rejeu du meme webhook : idempotent, aucun double decrement.
        mvc.perform(post("/webhooks/paiement").with(anonymous())
                        .param("id", referencePrestataire))
                .andExpect(status().isOk());
        assertThat(plaquettes.getQuantiteStock()).isEqualTo(8);
        assertThat(ampoule.getQuantiteStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("echoue : paiement ECHOUE, commande toujours EN_ATTENTE_PAIEMENT, stock intact")
    void parcoursEchoue() throws Exception {
        UUID referenceCommande = commander();
        String referencePrestataire = initierPaiement(referenceCommande);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.ECHOUE);

        mvc.perform(post("/webhooks/paiement").with(anonymous())
                        .param("id", referencePrestataire))
                .andExpect(status().isOk());

        assertThat(commandes.findByReference(referenceCommande).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(paiements.findByReferenceMollie(referencePrestataire).orElseThrow().getStatut())
                .isEqualTo(StatutPaiement.ECHOUE);
        assertThat(plaquettes.getQuantiteStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("identifiant forge inconnu : 404, rien ne bouge")
    void webhookForge() throws Exception {
        commander();

        mvc.perform(post("/webhooks/paiement").with(anonymous())
                        .param("id", "tr_forge_inconnu"))
                .andExpect(status().isNotFound());
    }
}
