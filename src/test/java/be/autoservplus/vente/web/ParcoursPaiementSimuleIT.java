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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
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
    @Autowired private be.autoservplus.vente.service.PrestatairePaiementFictif prestataireFictif;

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

        // Le retour passe par la meme porte qu un prestataire reel : /retour, qui
        // reconcilie, puis la confirmation.
        mvc.perform(post(urlPaiement).with(csrf()).param("reussite", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + reference + "/retour"));
        mvc.perform(get("/commande/{ref}/retour", reference))
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
    @DisplayName("le retour reconcilie meme si AUCUNE notification n est arrivee")
    void reconciliationSansNotification() throws Exception {
        UUID reference = commander();
        String urlPaiement = urlDePaiement(reference);
        String referencePrestataire = urlPaiement.substring(urlPaiement.lastIndexOf('/') + 1);

        // Le paiement aboutit CHEZ le prestataire, et rien ne le notifie — exactement
        // ce qui se produit lorsque le site n est pas joignable depuis l exterieur et
        // qu aucune URL de notification n a donc ete transmise.
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        assertThat(commandes.findByReference(reference).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);

        // Sans cette reconciliation, la commande resterait en attente puis serait
        // annulee par le job RM-21 : un encaissement reel sans commande en face.
        mvc.perform(get("/commande/{ref}/retour", reference))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + reference + "/confirmation"));

        assertThat(commandes.findByReference(reference).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.PAYEE);
    }

    @Test
    @DisplayName("le retour ne conclut rien de lui-meme : sans paiement abouti, la commande attend")
    void retourNEstPasUnePreuve() throws Exception {
        UUID reference = commander();
        urlDePaiement(reference);

        // Le membre revient sans avoir paye — le prestataire renvoie aussi apres un
        // abandon. Visiter l adresse ne doit rien confirmer.
        mvc.perform(get("/commande/{ref}/retour", reference))
                .andExpect(status().is3xxRedirection());

        assertThat(commandes.findByReference(reference).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
    }

    @Test
    @DisplayName("un retour sur une commande jamais mise en paiement ne rompt pas")
    void retourSansAucunPaiement() throws Exception {
        UUID reference = commander();

        // Aucune tentative n a ete initiee : il n y a rien a relire chez le
        // prestataire, et l ecran doit malgre tout repondre.
        mvc.perform(get("/commande/{ref}/retour", reference))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + reference + "/confirmation"));
    }

    @Test
    @DisplayName("le retour d autrui repond 404, jamais 403")
    void retourDAutrui() throws Exception {
        UUID reference = commander();

        mvc.perform(get("/commande/{ref}/retour", reference).with(user("luc@exemple.be")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("une fois payee, la confirmation ne propose plus de payer")
    void plusDeBoutonDePaiementApresPaiement() throws Exception {
        UUID reference = commander();
        String urlPaiement = urlDePaiement(reference);
        mvc.perform(post(urlPaiement).with(csrf()).param("reussite", "true"));
        mvc.perform(get("/commande/{ref}/retour", reference));

        // Reproposer le paiement a qui vient de payer invite a payer deux fois.
        mvc.perform(get("/commande/{ref}/confirmation", reference))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("/payer"))));
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

    @Test
    @DisplayName("notification puis retour : deux lignes distinctes, une seule transition")
    void lesDeuxDeclencheursSeLisentCoteACote(CapturedOutput journal) throws Exception {
        // docs/deploiement.md §B.5 promet de « voir les deux arriver tour a tour, sans
        // double facture ». Avant ce lot la promesse n etait pas tenable : aucune ligne
        // de journal ne distinguait le retour du membre de la notification serveur a
        // serveur, et rien ne disait laquelle des deux avait ecrit quelque chose.
        UUID reference = commander();
        String urlPaiement = urlDePaiement(reference);
        String referencePrestataire = urlPaiement.substring(urlPaiement.lastIndexOf('/') + 1);
        String numero = commandes.findByReference(reference).orElseThrow().getNumero();

        // Le paiement aboutit chez le prestataire sans que rien ne l ait encore
        // constate ici : c est la notification qui arrive la premiere.
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").param("id", referencePrestataire))
                .andExpect(status().isOk());

        assertThat(journal).contains("Notification du prestataire pour le paiement "
                + referencePrestataire + " : statut relu = REUSSI, commande passee PAYEE, "
                + "facture emise.");

        // Le membre revient ensuite. Meme chemin, meme relecture — mais plus rien a
        // ecrire, et c est ce que la ligne doit rendre visible.
        mvc.perform(get("/commande/{ref}/retour", reference))
                .andExpect(status().is3xxRedirection());

        assertThat(journal).contains("Retour du membre pour la commande " + numero
                + " : statut relu chez le prestataire = REUSSI, deja traite, "
                + "aucune ecriture.");

        // L idempotence n est pas seulement annoncee par les libelles : une seule
        // transition a eu lieu, donc un seul decrement de stock.
        assertThat(pieces.findById(filtre.getId()).orElseThrow().getQuantiteStock())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("retour sans tentative partie : la ligne le dit au lieu de se taire")
    void retourSansTentativeEstTraceAussi(CapturedOutput journal) throws Exception {
        UUID reference = commander();
        String numero = commandes.findByReference(reference).orElseThrow().getNumero();

        mvc.perform(get("/commande/{ref}/retour", reference))
                .andExpect(status().is3xxRedirection());

        // Un silence se confondrait avec une panne de journalisation. L exploitant doit
        // pouvoir distinguer « rien a relire » de « rien ne s est execute ».
        assertThat(journal).contains("Retour du membre pour la commande " + numero
                + " : statut relu chez le prestataire = aucun, aucune tentative n a "
                + "quitte le site.");
    }
}
