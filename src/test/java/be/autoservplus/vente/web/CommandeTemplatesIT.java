package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PanierRepository;
import be.autoservplus.vente.service.PanierService;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conversion du panier en commande de bout en bout (F14) : recapitulatif RM-30,
 * mention legale du bouton (art. VI.45 CDE), CGV revalidees serveur, PRG vers la
 * confirmation, preuve d acceptation en base, refus total sur stock insuffisant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Templates commande (integration)")
class CommandeTemplatesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private PanierRepository paniers;
    @Autowired private CommandeRepository commandes;
    @Autowired private ConsentementRepository consentements;
    @Autowired private VersionsDocumentsService versionsDocuments;
    @Autowired private EntityManager entityManager;

    private Piece plaquettes;
    private Piece ampoule;

    @BeforeEach
    void setUp() {
        utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Categorie freinage = categories.save(
                new Categorie("IT-CMD-FRE", "Freinage", TypeCategorie.PIECE));
        plaquettes = new Piece(freinage, "IT-CMD-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
        plaquettes = pieces.save(plaquettes);
        ampoule = new Piece(freinage, "IT-CMD-002", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(5);
        ampoule = pieces.save(ampoule);
    }

    /** Meme cas a taux mixtes que les tests unitaires : totaux 70,01 / 10,20 / 80,21. */
    private void remplirLePanier() {
        panierService.ajouterPiece("marie@exemple.be", ampoule.getReference(), 3);
        panierService.ajouterPiece("marie@exemple.be", plaquettes.getReference(), 2);
    }

    @Test
    @DisplayName("GET /commande rend le recapitulatif RM-30 et le bouton a mention legale")
    void recapitulatifRendu() throws Exception {
        remplirLePanier();

        mvc.perform(get("/commande").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Récapitulatif de commande")))
                .andExpect(content().string(containsString("30,03")))
                .andExpect(content().string(containsString("70,01")))
                .andExpect(content().string(containsString("10,20")))
                .andExpect(content().string(containsString("80,21")))
                // Art. VI.45 CDE : la formule exacte, pas « Valider » ni « Payer ».
                .andExpect(content().string(containsString("Commande avec obligation de paiement")))
                .andExpect(content().string(containsString("conditions générales de vente")));
    }

    @Test
    @DisplayName("POST sans case CGV : refus serveur, aucune commande ni preuve en base")
    void refusSansCgv() throws Exception {
        remplirLePanier();

        mvc.perform(post("/commande").with(csrf()).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vous devez accepter")));

        assertThat(commandes.count()).isZero();
        assertThat(consentements.count()).isZero();
    }

    @Test
    @DisplayName("conversion nominale : PRG, commande EN_ATTENTE_PAIEMENT, preuve CGV, panier vide")
    void conversionNominale() throws Exception {
        remplirLePanier();

        String urlConfirmation = mvc.perform(post("/commande").with(csrf())
                        .locale(Locale.FRENCH).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/commande/*/confirmation"))
                .andReturn().getResponse().getRedirectedUrl();

        // Artefact de test : toute la classe partage une transaction et un contexte de
        // persistance, la collection du panier resterait a jour d'avant-conversion.
        // En production, chaque requete a son propre contexte. flush + clear simulent
        // ce rechargement.
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(urlConfirmation).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CMD-")))
                .andExpect(content().string(containsString("80,21")))
                // Le module paiement a remplace le placeholder par le vrai depart.
                .andExpect(content().string(containsString("Procéder au paiement")));

        List<Commande> enBase = commandes.findAll();
        assertThat(enBase).hasSize(1);
        assertThat(enBase.get(0).getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(enBase.get(0).getMontantTvac()).isEqualByComparingTo("80.21");

        List<Consentement> preuves = consentements
                .findByUtilisateurEmailIgnoreCaseAndTypeDocument(
                        "marie@exemple.be", TypeDocumentConsentement.CGV);
        assertThat(preuves).hasSize(1);
        // Comparee a la version REELLEMENT en vigueur et non a un identifiant ecrit ici :
        // ce que ce test doit etablir, c est que la conversion fige la version du jour,
        // pas laquelle elle est. Le litteral « CGV-2026-01 » qui figurait la a fait
        // echouer ce test a la publication de CGV-2026-02 (V35), alors que le
        // comportement verifie n avait pas bouge — un test couple au calendrier
        // editorial des conditions generales, ce qui n est pas son sujet.
        assertThat(preuves.get(0).getVersionAcceptee())
                .isEqualTo(versionsDocuments.versionCourante(TypeDocumentVersionne.CGV));
        assertThat(preuves.get(0).isAccorde()).isTrue();
        assertThat(preuves.get(0).getAdresseIp()).isEqualTo("127.0.0.1");

        // Les lignes ont ete DEPLACEES : preuve COTE BASE, apres vidage de la session
        // (le clear ci-dessus) — le panier RECHARGE depuis PostgreSQL n'a plus aucune
        // ligne, ce n'est pas un etat d'instance en memoire. La gestion du piege
        // orphanRemoval est donc verifiee la ou elle compte : sur les FK.
        Panier recharge = paniers.findByMembreEmail("marie@exemple.be").orElseThrow();
        assertThat(recharge.getLignes())
                .as("Le panier-contenant survit mais ne contient plus de ligne active")
                .isEmpty();

        mvc.perform(get("/panier").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("Votre panier est vide.")))
                .andExpect(content().string(containsString("Panier (0)")));
    }

    @Test
    @DisplayName("stock devenu insuffisant : refus TOTAL, panier intact, zero commande, zero preuve")
    void stockInsuffisantRefusTotal() throws Exception {
        remplirLePanier();
        // Un autre membre a rafle le stock entre l ajout au panier et la conversion.
        plaquettes.setQuantiteStock(1);
        pieces.saveAndFlush(plaquettes);

        mvc.perform(post("/commande").with(csrf()).locale(Locale.FRENCH).param("cgv", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stock insuffisant")))
                .andExpect(content().string(containsString("Plaquettes avant")));

        assertThat(commandes.count()).isZero();
        assertThat(consentements.count()).isZero();
        mvc.perform(get("/panier").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("Plaquettes avant")))
                .andExpect(content().string(containsString("Panier (5)")));
    }

    @Test
    @DisplayName("POST sans jeton CSRF est rejete")
    void csrfRequis() throws Exception {
        mvc.perform(post("/commande").param("cgv", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("non authentifie : redirection vers la connexion")
    void accesRefuseNonAuthentifie() throws Exception {
        mvc.perform(get("/commande"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/connexion"));
    }
}
