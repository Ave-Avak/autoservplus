package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Premier paiement après un redémarrage, quand la base garde les références simulées
 * des sessions précédentes.
 *
 * <p>La classe a son propre contexte, donc un bouchon neuf, comme après un
 * redémarrage de la démonstration. Le compteur qu'il portait repartait de 1 et
 * rendait {@code tr_fictif_0001}, que la base contenait déjà : le paiement tombait en
 * 500 sur {@code uq_paiement_mollie}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "marie.redemarrage@exemple.be")
@DisplayName("Paiement simulé après un redémarrage (intégration)")
class PaiementApresRedemarrageIT extends SocleIntegration {

    private static final String EMAIL = "marie.redemarrage@exemple.be";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private PaiementRepository paiements;

    @Test
    @DisplayName("la base porte déjà tr_fictif_0001 : le premier paiement passe, sous une autre référence")
    void premierPaiementApresRedemarrage() throws Exception {
        Utilisateur marie = utilisateurs.save(MembresDeTest.membre(EMAIL, "$2a$12$h", "Dupont", "Marie"));
        Categorie moteur = categories.save(new Categorie("IT-RED-MOT", "Moteur", TypeCategorie.PIECE));
        Piece filtre = new Piece(moteur, "IT-RED-001", "Filtre a huile", new BigDecimal("12.50"));
        filtre.setQuantiteStock(5);
        filtre = pieces.save(filtre);

        // Paiement d'une session précédente, sous l'ancienne numérotation.
        Commande ancienne = commandes.save(new Commande("CMD-2026-9001", marie,
                new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                Instant.parse("2026-10-27T11:05:20Z")));
        Paiement precedent = new Paiement(ancienne, ancienne.getMontantTvac(),
                Instant.parse("2026-10-27T11:05:20Z"));
        precedent.enregistrerReferencePrestataire("tr_fictif_0001");
        paiements.saveAndFlush(precedent);

        panierService.ajouterPiece(EMAIL, filtre.getReference(), 1);
        String confirmation = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID reference = UUID.fromString(confirmation
                .replace("/commande/", "").replace("/confirmation", ""));

        String page = mvc.perform(post("/commande/{ref}/payer", reference).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(page).matches("/paiement-fictif/tr_fictif_[0-9a-f]{16}");
        assertThat(paiements.findByReferenceMollie(page.substring(page.lastIndexOf('/') + 1)))
                .isPresent();
    }
}
