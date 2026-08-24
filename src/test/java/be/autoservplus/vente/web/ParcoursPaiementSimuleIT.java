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
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.PanierService;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaine marchande parcourue COMME UN MEMBRE LA PARCOURT : chaque etape suit la
 * redirection precedente, du panier jusqu a la commande payee.
 *
 * <p><b>Ce que ce test verrouille, et pourquoi il a fallu l ecrire.</b>
 * {@code PaiementIT} couvrait deja la meme chaine, mais il sautait une marche :
 * apres avoir constate que l initiation redirige vers {@code /paiement-fictif/*},
 * il abandonnait cette URL et appelait {@code POST /webhooks/paiement} en direct.
 * Or aucun controleur ne servait cette adresse — le membre qui suivait la
 * redirection recevait un 404, et la chaine marchande etait donc verte en
 * integration et impraticable a l ecran.</p>
 *
 * <p>La lecon vaut au-dela du defaut : <b>un test qui construit lui-meme l etape
 * suivante ne prouve pas que l utilisateur peut l atteindre</b>. Ici aucune URL
 * n est fabriquee — toutes sont lues dans la reponse precedente. C est ce qui rend
 * le 404 impossible a reintroduire en silence.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Parcours marchand complet par l interface (bouchon)")
class ParcoursPaiementSimuleIT {

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

    private Piece filtre;

    @BeforeEach
    void setUp() {
        utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        utilisateurs.save(new Utilisateur(
                "luc@exemple.be", "$2a$12$h", "Martin", "Luc", TypeUtilisateur.MEMBRE));
        Categorie moteur = categories.save(
                new Categorie("IT-SIM-MOT", "Moteur", TypeCategorie.PIECE));
        filtre = new Piece(moteur, "IT-SIM-001", "Filtre a huile", new BigDecimal("12.50"));
        filtre.setQuantiteStock(7);
        filtre = pieces.save(filtre);
    }

    /** Panier puis commande ; rend la reference lue dans la redirection. */
    private UUID commander() throws Exception {
        panierService.ajouterPiece("marie@exemple.be", filtre.getReference(), 2);
        String redirection = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection
                .replace("/commande/", "").replace("/confirmation", ""));
    }

    /** Initiation ; rend l URL de paiement TELLE QUE le membre la recoit. */
    private String urlDePaiement(UUID commande) throws Exception {
        return mvc.perform(post("/commande/{ref}/payer", commande).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    @DisplayName("du panier a la commande payee sans jamais fabriquer d URL ni appeler le webhook")
    void parcoursCompletParLInterface() throws Exception {
        UUID reference = commander();
        String urlPaiement = urlDePaiement(reference);
        assertThat(urlPaiement).startsWith("/paiement-fictif/");

        // L etape que PaiementIT n empruntait pas : suivre la redirection. Elle
        // repondait 404 avant ce lot.
        mvc.perform(get(urlPaiement))
                .andExpect(status().isOk())
                // La page s annonce comme simulee : une page qui se ferait passer pour
                // une vraie page de paiement serait indefendable.
                .andExpect(content().string(containsString("simul")))
                .andExpect(content().string(containsString("CMD-")));

        mvc.perform(post(urlPaiement).with(csrf()).param("reussite", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + reference + "/confirmation"));

        Commande commande = commandes.findByReference(reference).orElseThrow();
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(commande.getDatePaiement()).isNotNull();
        assertThat(pieces.findById(filtre.getId()).orElseThrow().getQuantiteStock())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("l echec simule laisse la commande en attente et le stock intact")
    void echecSimule() throws Exception {
        UUID reference = commander();
        String urlPaiement = urlDePaiement(reference);

        mvc.perform(post(urlPaiement).with(csrf()).param("reussite", "false"))
                .andExpect(status().is3xxRedirection());

        Commande commande = commandes.findByReference(reference).orElseThrow();
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        String referencePrestataire = urlPaiement.substring(urlPaiement.lastIndexOf('/') + 1);
        assertThat(paiements.findByReferenceMollie(referencePrestataire).orElseThrow()
                .getStatut()).isEqualTo(StatutPaiement.ECHOUE);
        assertThat(pieces.findById(filtre.getId()).orElseThrow().getQuantiteStock())
                .isEqualTo(7);
    }

    @Test
    @DisplayName("rejouer l issue est sans double effet : le stock ne bouge qu une fois")
    void rejeuSansDoubleEffet() throws Exception {
        UUID reference = commander();
        String urlPaiement = urlDePaiement(reference);

        mvc.perform(post(urlPaiement).with(csrf()).param("reussite", "true"))
                .andExpect(status().is3xxRedirection());
        // Le membre rafraichit, ou revient en arriere : le traitement est idempotent
        // parce qu il passe par le meme chemin que le webhook rejoue.
        mvc.perform(post(urlPaiement).with(csrf()).param("reussite", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(pieces.findById(filtre.getId()).orElseThrow().getQuantiteStock())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("le paiement d autrui repond 404, jamais 403")
    void paiementDAutrui() throws Exception {
        String urlPaiement = urlDePaiement(commander());

        // Luc est authentifie, mais la commande est celle de Marie. Un 403
        // confirmerait que la reference existe ; le projet repond 404 partout ou une
        // ressource est nominative.
        mvc.perform(get(urlPaiement).with(user("luc@exemple.be")))
                .andExpect(status().isNotFound());
        mvc.perform(post(urlPaiement).with(user("luc@exemple.be")).with(csrf())
                        .param("reussite", "true"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("une reference de prestataire inconnue repond 404")
    void referenceInconnue() throws Exception {
        mvc.perform(get("/paiement-fictif/tr_inexistant"))
                .andExpect(status().isNotFound());
    }
}
