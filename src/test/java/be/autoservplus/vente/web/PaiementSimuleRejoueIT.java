package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Page de paiement simulé rouverte par le bouton Retour sur une tentative déjà close :
 * l'issue n'est pas rejouée, le membre revient à sa commande avec un message.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(username = PaiementSimuleRejoueIT.EMAIL)
@DisplayName("Paiement simulé rejoué après une issue (intégration)")
class PaiementSimuleRejoueIT extends SocleIntegration {

    static final String EMAIL = "rejoue.paiement@exemple.be";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private PaiementRepository paiements;

    private Piece filtre;

    @BeforeAll
    void catalogue() {
        utilisateurs.save(MembresDeTest.membre(EMAIL, "$2a$12$h", "Rejoue", "Anne"));
        Categorie moteur = categories.save(new Categorie("IT-REJ-MOT", "Moteur", TypeCategorie.PIECE));
        filtre = new Piece(moteur, "IT-REJ-001", "Filtre a air", new BigDecimal("15.00"));
        filtre.setQuantiteStock(10);
        filtre = pieces.save(filtre);
    }

    @Test
    @DisplayName("échec, puis « réussi » sur la même page : refusé, retour à la commande, paiement resté en échec")
    void echecPuisReussite() throws Exception {
        panierService.ajouterPiece(EMAIL, filtre.getReference(), 1);
        String confirmation = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID commande = UUID.fromString(confirmation.replace("/commande/", "").replace("/confirmation", ""));
        String page = mvc.perform(post("/commande/{ref}/payer", commande).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = page.substring(page.lastIndexOf('/') + 1);

        mvc.perform(post(page).with(csrf()).param("issue", "ECHEC"))
                .andExpect(redirectedUrl("/commande/" + commande + "/retour"));

        mvc.perform(post(page).with(csrf()).param("issue", "REUSSITE"))
                .andExpect(redirectedUrl("/commandes/" + commande))
                .andExpect(flash().attributeExists("erreur"));
        mvc.perform(get(page))
                .andExpect(redirectedUrl("/commandes/" + commande));
        assertThat(paiements.findByReferenceMollie(referencePrestataire).orElseThrow().getStatut())
                .isEqualTo(StatutPaiement.ECHOUE);
    }
}
