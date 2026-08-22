package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu des ecrans du panier (F13) contre le contexte Spring complet : detail
 * HTVA / TVA / TVAC (RM-30), formulaire d ajout depuis la fiche piece, compteur
 * d en-tete, CSRF et protection d acces. Les requetes fixent la locale francaise :
 * les libelles passent par le MessageSource et MockMvc est anglophone par defaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Templates panier (integration)")
class PanierTemplatesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;

    private Piece plaquettes;
    private Piece ampoule;

    @BeforeEach
    void setUp() {
        utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Categorie freinage = categories.save(
                new Categorie("IT-PAN-FRE", "Freinage", TypeCategorie.PIECE));
        plaquettes = new Piece(freinage, "IT-PAN-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
        plaquettes = pieces.save(plaquettes);
        ampoule = new Piece(freinage, "IT-PAN-002", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(5);
        ampoule = pieces.save(ampoule);
    }

    /** Meme cas a taux mixtes que le test unitaire RM-30, verifie a la main. */
    private void remplirLePanier() {
        panierService.ajouterPiece("marie@exemple.be", ampoule.getReference(), 3);
        panierService.ajouterPiece("marie@exemple.be", plaquettes.getReference(), 2);
    }

    @Test
    @DisplayName("GET /panier rend le detail HTVA / TVA / TVAC par ligne et en totaux (RM-30)")
    void detailRm30() throws Exception {
        remplirLePanier();

        mvc.perform(get("/panier").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mon panier")))
                // Ligne ampoule : 30,03 HTVA / 1,80 TVA / 31,83 TVAC a 6 %.
                .andExpect(content().string(containsString("30,03")))
                .andExpect(content().string(containsString("1,80")))
                .andExpect(content().string(containsString("31,83")))
                // Ligne plaquettes : 39,98 / 8,40 / 48,38 a 21 %.
                .andExpect(content().string(containsString("39,98")))
                .andExpect(content().string(containsString("48,38")))
                // Totaux sommes ligne a ligne.
                .andExpect(content().string(containsString("70,01")))
                .andExpect(content().string(containsString("10,20")))
                .andExpect(content().string(containsString("80,21")))
                // Compteur d en-tete : 5 articles.
                .andExpect(content().string(containsString("Panier (5)")));
    }

    @Test
    @DisplayName("la fiche piece propose le formulaire d'ajout ; l'ajout aboutit puis s'affiche")
    void ajoutDepuisLaFiche() throws Exception {
        mvc.perform(get("/pieces/{ref}", plaquettes.getReference()).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ajouter au panier")))
                .andExpect(content().string(containsString("name=\"quantite\"")))
                .andExpect(content().string(containsString("Panier (0)")));

        mvc.perform(post("/panier/ajouter").with(csrf()).locale(Locale.FRENCH)
                        .param("reference", plaquettes.getReference().toString())
                        .param("quantite", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("message"));

        mvc.perform(get("/panier").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("Plaquettes avant")))
                .andExpect(content().string(containsString("Panier (2)")));
    }

    @Test
    @DisplayName("stock insuffisant : retour au panier avec la quantite disponible dans le message")
    void stockInsuffisantMessage() throws Exception {
        mvc.perform(post("/panier/ajouter").with(csrf()).locale(Locale.FRENCH)
                        .param("reference", ampoule.getReference().toString())
                        .param("quantite", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erreur", containsString("5")));
    }

    @Test
    @DisplayName("une piece devenue inactive apres ajout est signalee dans le recapitulatif (RM-28)")
    void pieceInactiveSignalee() throws Exception {
        remplirLePanier();
        plaquettes.desactiver();
        pieces.saveAndFlush(plaquettes);

        mvc.perform(get("/panier").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ne sont plus proposés à la vente")));
    }

    @Test
    @DisplayName("vider demande une confirmation explicite, puis le panier est vide")
    void viderAvecConfirmation() throws Exception {
        remplirLePanier();

        mvc.perform(get("/panier/vider").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Voulez-vous vraiment")));

        mvc.perform(post("/panier/vider").with(csrf()).locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/panier").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("Votre panier est vide.")))
                .andExpect(content().string(not(containsString("Plaquettes avant"))));
    }

    @Test
    @DisplayName("POST sans jeton CSRF est rejete")
    void csrfRequis() throws Exception {
        mvc.perform(post("/panier/ajouter")
                        .param("reference", plaquettes.getReference().toString())
                        .param("quantite", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("non authentifie : redirection vers la connexion, et pas de lien panier sur le catalogue")
    void accesRefuseNonAuthentifie() throws Exception {
        mvc.perform(get("/panier"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/connexion"));

        // La fiche publique reste accessible, sans lien panier ni compteur.
        mvc.perform(get("/pieces/{ref}", plaquettes.getReference()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Panier ("))));
    }
}
